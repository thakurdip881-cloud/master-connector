@echo off
chcp 65001 >nul
title DIP AI
setlocal EnableDelayedExpansion
set "TMPQ=%TEMP%\dip-ai-q.txt"
set "TMPA=%TEMP%\dip-ai-a.txt"

if /i "%~1"=="--copy" goto FROMCLIP
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
echo   4) कॉपी किया हुआ text से काम (जवाब अपने-आप कॉपी)
echo   5) सवाल लिखें, जवाब अपने-आप कॉपी
echo   0) बाहर निकलें
set "C="
set /p C=चुनें:
if "%C%"=="1" goto ASK
if "%C%"=="2" goto PICK
if "%C%"=="3" goto INSTALL
if "%C%"=="4" goto FROMCLIP
if "%C%"=="5" goto ASKCOPY
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

:FROMCLIP
powershell -NoProfile -Command "[Console]::OutputEncoding=[Text.Encoding]::UTF8; $t=Get-Clipboard -Raw; if($t){[IO.File]::WriteAllText($env:TMPQ,$t)}else{exit 1}"
if errorlevel 1 (
  echo कुछ कॉपी नहीं किया हुआ है। पहले कोई text कॉपी करें।
  goto DONE
)
echo.
echo कॉपी किया हुआ काम:
type "%TMPQ%"
echo.
goto ANSWERCOPY

:ASKCOPY
set "Q="
set /p Q=सवाल/काम:
if not defined Q goto MENU
powershell -NoProfile -Command "[IO.File]::WriteAllText($env:TMPQ,$env:Q)"
goto ANSWERCOPY

:ANSWERCOPY
for %%T in (gemini claude codex) do (
  where %%T >nul 2>&1
  if !errorlevel!==0 (
    echo.
    echo ^> %%T से जवाब ले रहा हूँ...
    call :ASKTOOL %%T
    if !errorlevel!==0 (
      for %%S in ("%TMPA%") do if %%~zS GTR 0 (
        type "%TMPA%"
        echo.
        powershell -NoProfile -Command "Get-Content -Raw -Encoding UTF8 $env:TMPA | Set-Clipboard"
        echo जवाब कॉपी हो गया - अब किसी भी ऐप में Ctrl+V से Paste करें।
        goto DONE
      )
    )
    echo %%T नहीं चला, अगला टूल आज़माता हूँ...
  )
)
echo कोई AI टूल नहीं चला। मेन्यू में 3 दबाकर इंस्टॉल करें।
goto DONE

:ASKTOOL
rem सवाल फ़ाइल से पढ़कर सीधा जवाब, जवाब फ़ाइल में
powershell -NoProfile -Command "$q=Get-Content -Raw -Encoding UTF8 $env:TMPQ; switch('%~1'){'gemini'{& gemini -p $q}'claude'{& claude -p $q}'codex'{& codex exec $q}} | Out-File -Encoding UTF8 $env:TMPA; exit $LASTEXITCODE"
exit /b %errorlevel%

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
echo   [OK] कॉपी-पेस्ट
exit /b

:DONE
echo.
pause
goto MENU
