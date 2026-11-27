@echo off
REM Replaces the ENTIRE contents of a GitHub repo with this project.
REM Usage: tools\replace_repo.bat https://github.com/USER/REPO.git [branch]
setlocal
set REPO=%1
set BRANCH=%2
if "%REPO%"=="" (echo Usage: replace_repo.bat repo-url [branch] & exit /b 1)
if "%BRANCH%"=="" set BRANCH=main
set SRC=%~dp0..
set TMP=%TEMP%\maherma_sync_%RANDOM%
git clone %REPO% "%TMP%" || exit /b 1
pushd "%TMP%"
git checkout %BRANCH% 2>nul || git checkout -b %BRANCH%
for /d %%D in (*) do if /i not "%%D"==".git" rmdir /s /q "%%D"
for %%F in (*) do del /f /q "%%F"
for %%F in (.gitignore) do del /f /q "%%F" 2>nul
robocopy "%SRC%" "%TMP%" /E /XD .git build .gradle /XF local.properties >nul
git add -A
git diff --cached --quiet && (echo Nothing changed. & popd & exit /b 0)
git commit -m "Replace with latest Maherma build"
git push -u origin %BRANCH%
popd
rmdir /s /q "%TMP%"
echo Done: repo now matches this folder exactly.
