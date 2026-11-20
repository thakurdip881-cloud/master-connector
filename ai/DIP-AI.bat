@echo off
chcp 65001 >nul
title DIP AI
setlocal EnableDelayedExpansion

if not "%~1"=="" (
  set "TASK=%*"
  goto AUTO
)

:MENU
cls
echo ==============================
echo      DIP AI - कमांड टूल
echo ==============================
call :STATUS
echo.
echo   1) काम लिखें (अपने-आप सबसे अच्छा टूल)
echo   2) खुद टूल चुनें
echo   3) टूल इंस्टॉल करें
echo   0) बाहर निकलें
set "C="
set /p C=चुनें:
if "%C%"=="1" goto ASK
if "%C%"=="2" goto PICK
if "%C%"=="3" goto INSTALL
if "%C%"=="0" exit /b
goto MENU

:ASK
set "TASK="
set /p TASK=क्या काम करवाना है?
goto AUTO

:AUTO
set "TRIED=0"
for %%T in (gemini claude codex) do (
  where %%T >nul 2>&1
  if !errorlevel!==0 (
    set "TRIED=1"
    echo.
    echo ^> %%T से काम करवा रहा हूँ...
    call :RUN %%T
    if !errorlevel!==0 goto DONE
    echo %%T नहीं चला, अगला टूल आज़माता हूँ...
  )
)
if "!TRIED!"=="0" echo कोई AI टूल इंस्टॉल नहीं है। मेन्यू में 3 दबाकर इंस्टॉल करें।
goto DONE

:PICK
echo   1) Gemini (फ़्री)   2) Claude Code   3) Codex
set "N="
set /p N=टूल:
set "T="
if "%N%"=="1" set "T=gemini"
if "%N%"=="2" set "T=claude"
if "%N%"=="3" set "T=codex"
if "%T%"=="" goto MENU
where %T% >nul 2>&1 || (echo यह टूल इंस्टॉल नहीं है। & goto DONE)
set "TASK="
set /p TASK=क्या काम करवाना है? (खाली छोड़ें तो सीधे खुलेगा)
call :RUN %T%
goto DONE

:RUN
if "%~1"=="gemini" (
  if defined TASK (call gemini -i "!TASK!") else (call gemini)
) else (
  if defined TASK (call %~1 "!TASK!") else (call %~1)
)
exit /b !errorlevel!

:INSTALL
where npm >nul 2>&1 || (
  echo पहले Node.js डालें: https://nodejs.org  ^(LTS वाला^), फिर यह फ़ाइल दोबारा खोलें।
  start https://nodejs.org
  goto DONE
)
call npm install -g @google/gemini-cli && echo Gemini (फ़्री) तैयार
set "A="
set /p A=Claude Code भी डालें? (पैसे वाला Claude प्लान चाहिए) [y/N]:
if /i "%A%"=="y" call npm install -g @anthropic-ai/claude-code
set "A="
set /p A=Codex भी डालें? (ChatGPT खाता चाहिए) [y/N]:
if /i "%A%"=="y" call npm install -g @openai/codex
echo हो गया। पहली बार टूल खुलने पर लॉगिन करें।
goto DONE

:STATUS
echo.
for %%T in (gemini claude codex) do (
  where %%T >nul 2>&1 && (echo   [OK] %%T) || (echo   [--] %%T ^(इंस्टॉल नहीं^))
)
exit /b

:DONE
echo.
pause
goto MENU
