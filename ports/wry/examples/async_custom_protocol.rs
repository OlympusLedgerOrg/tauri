// Copyright 2020-2023 Tauri Programme within The Commons Conservancy
// SPDX-License-Identifier: Apache-2.0
// SPDX-License-Identifier: MIT

use std::path::PathBuf;

use tao::{
  event::{Event, WindowEvent},
  event_loop::{ControlFlow, EventLoop},
  window::WindowBuilder,
};
use wry::{
  http::{header::CONTENT_TYPE, Request, Response, StatusCode},
  WebViewBuilder,
};

pub fn main() -> wry::Result<()> {
  let event_loop = EventLoop::new();
  let window = WindowBuilder::new().build(&event_loop).unwrap();

  let builder = WebViewBuilder::new()
    .with_asynchronous_custom_protocol("wry".into(), move |_webview_id, request, responder| {
      match get_wry_response(request) {
        Ok(http_response) => responder.respond(http_response),
        Err(e) => responder.respond(
          http::Response::builder()
            .header(CONTENT_TYPE, "text/plain")
            .status(500)
            .body(e.to_string().as_bytes().to_vec())
            .unwrap(),
        ),
      }
    })
    // tell the webview to load the custom protocol
    .with_url("wry://localhost");

  #[cfg(any(
    target_os = "windows",
    target_os = "macos",
    target_os = "ios",
    target_os = "android"
  ))]
  let _webview = builder.build(&window)?;
  #[cfg(not(any(
    target_os = "windows",
    target_os = "macos",
    target_os = "ios",
    target_os = "android"
  )))]
  let _webview = {
    use tao::platform::unix::WindowExtUnix;
    use wry::WebViewBuilderExtUnix;
    let vbox = window.default_vbox().unwrap();
    builder.build_gtk(vbox)?
  };

  event_loop.run(move |event, _, control_flow| {
    *control_flow = ControlFlow::Wait;

    if let Event::WindowEvent {
      event: WindowEvent::CloseRequested,
      ..
    } = event
    {
      *control_flow = ControlFlow::Exit
    }
  });
}

fn get_wry_response(
  request: Request<Vec<u8>>,
) -> Result<http::Response<Vec<u8>>, Box<dyn std::error::Error>> {
  let path = request.uri().path();
  // Read the file content from file path
  let root = PathBuf::from("examples/custom_protocol");
  let path = if path == "/" {
    "index.html"
  } else {
    //  removing leading slash
    &path[1..]
  };
  let root = std::fs::canonicalize(root)?;
  let file_path = std::fs::canonicalize(root.join(path))?;
  if !file_path.starts_with(&root) {
    return Ok(
      Response::builder()
        .status(StatusCode::NOT_FOUND)
        .header(CONTENT_TYPE, "text/plain")
        .body(b"not found".to_vec())?,
    );
  }
  let content = std::fs::read(file_path)?;

  // Return asset contents and mime types based on file extensions
  // If you don't want to do this manually, there are some crates for you.
  // Such as `infer` and `mime_guess`.
  let mimetype = if path.ends_with(".html") || path == "/" {
    Some("text/html")
  } else if path.ends_with(".js") {
    Some("text/javascript")
  } else if path.ends_with(".png") {
    Some("image/png")
  } else if path.ends_with(".wasm") {
    Some("application/wasm")
  } else {
    None
  };
  let Some(mimetype) = mimetype else {
    return Ok(
      Response::builder()
        .status(StatusCode::UNSUPPORTED_MEDIA_TYPE)
        .header(CONTENT_TYPE, "text/plain")
        .body(b"unsupported media type".to_vec())?,
    );
  };

  Response::builder()
    .header(CONTENT_TYPE, mimetype)
    .body(content)
    .map_err(Into::into)
}
