
@echo off
setlocal
where javac >nul 2>nul
if errorlevel 1 (
  echo JDK not found. Please install JDK 17+ and ensure javac is on PATH.
  exit /b 1
)
echo Compiling HZipLite.java ...
javac -encoding UTF-8 HZipLite.java || exit /b 1
echo Launching GUI...
java HZipLite
