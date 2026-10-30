//! tees the console output to a file

use std::fs::OpenOptions;
use std::io::Write;
use std::path::PathBuf;

pub fn path() -> PathBuf {
    std::env::var("LUNAGC_PATCH_LOG")
        .ok()
        .filter(|p| !p.is_empty())
        .map(PathBuf::from)
        .unwrap_or_else(|| std::env::temp_dir().join("lunagc-patch.log"))
}

/// fresh log per launch
pub fn start_session() {
    let _ = std::fs::write(path(), b"");
}

/// appends one line
pub fn write(line: &str) {
    if let Ok(mut file) = OpenOptions::new().create(true).append(true).open(path()) {
        let _ = writeln!(file, "{line}");
    }
}

/// println! that also hits the file
#[macro_export]
macro_rules! plog {
    ($($arg:tt)*) => {{
        let line = format!($($arg)*);
        println!("{}", line);
        $crate::log::write(&line);
    }};
}
