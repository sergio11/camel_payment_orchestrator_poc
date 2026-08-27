@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM
@REM Optional ENV vars:
@REM   MVNW_REPOURL - repo url base for downloading maven distribution
@REM   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
@REM   MVNW_VERBOSE - true: enable verbose log; others: silence the output
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET __MVNW_CMD__=
@SET __MVNW_ERROR__=
@SET __MVNW_PSMODULEP_SAVE=%PSModulePath%
@SET PSModulePath=
@FOR /F "usebackq tokens=1* delims==" %%A IN ("%~dp0\.mvn\wrapper\maven-wrapper.properties") DO @(
    IF "%%~A"=="wrapperUrl" SET "__MVNW_CMD__=%%~B"
    IF "%%~A"=="distributionUrl" SET "MW_distributionUrl=%%~B"
)
@IF "%__MVNW_CMD__%"=="" SET "__MVNW_CMD__=%MW_distributionUrl%"
@IF "%__MVNW_CMD__%"=="" SET "__MVNW_ERROR__=Cannot read distributionUrl property in %~dp0\.mvn\wrapper\maven-wrapper.properties"
@goto check_mvnw

:check_mvnw
@IF NOT "%__MVNW_ERROR__%"=="" goto report_error
@SET "MVNW_HASH_NAME=%__MVNW_CMD__%"
@FOR %%I IN ("%MVNW_HASH_NAME%") DO @SET "MVNW_HASH_NAME=%%~nI"
@SET "MVNW_HASH_NAME=%MVNW_HASH_NAME:.=-%"
@SET "MVNW_HASH=%MVNW_HASH_NAME%"
@SET "MAVEN_USERHOME=%USERPROFILE%\.m2"
@SET "MAVEN_HOME=%MAVEN_USERHOME%\wrapper\dists\%MVNW_HASH%"

@IF EXIST "%MAVEN_HOME%\bin\mvn.cmd" goto exec_mvn

@SET "WRAPPER_JAR=%MAVEN_HOME%\..\..\apache-maven-wrapper.jar"
@SET "WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain"

@IF NOT EXIST "%MAVEN_HOME%" @MD "%MAVEN_HOME%"

@SET "TMP_DOWNLOAD_DIR=%MAVEN_HOME%\..\..\tmp"
@IF NOT EXIST "%TMP_DOWNLOAD_DIR%" @MD "%TMP_DOWNLOAD_DIR%"

@SET "ZIP_NAME=%MVNW_HASH_NAME%.zip"
@SET "TMP_ZIP=%TMP_DOWNLOAD_DIR%\%ZIP_NAME%"

@ECHO Downloading from: %__MVNW_CMD__
@ECHO Downloading to: %TMP_ZIP%

@REM Download using PowerShell
@POWERSHELL -Command ^
  "$ProgressPreference = 'SilentlyContinue'; " ^
  "Invoke-WebRequest -Uri '%__MVNW_CMD__%' -OutFile '%TMP_ZIP%'"

@IF %ERRORLEVEL% NEQ 0 (
    SET "__MVNW_ERROR__=Failed to download from %__MVNW_CMD__%"
    goto report_error
)

@REM Extract
@POWERSHELL -Command ^
  "$ProgressPreference = 'SilentlyContinue'; " ^
  "Expand-Archive -Path '%TMP_ZIP%' -DestinationPath '%TMP_DOWNLOAD_DIR%' -Force"

@REM Move extracted folder to MAVEN_HOME
@FOR /D %%I IN ("%TMP_DOWNLOAD_DIR%\apache-maven-*") DO @(
    ROBOCOPY "%%I" "%MAVEN_HOME%" /E /IS /IT /NFL /NDL /NJH /NJS /NC /NS /NP >NUL
    @IF %ERRORLEVEL% LEQ 3 SET ERRORLEVEL=0
)

@RD /S /Q "%TMP_DOWNLOAD_DIR%" >NUL 2>&1

:exec_mvn
@SET "MVNW_CMD=%MAVEN_HOME%\bin\mvn.cmd"
@IF EXIST "%MVNW_CMD%" goto run_mvn
@SET "__MVNW_ERROR__=Cannot find %MVNW_CMD%"
@goto report_error

:run_mvn
@SET PSModulePath=%__MVNW_PSMODULEP_SAVE%
@"%MVNW_CMD%" %*
@goto end

:report_error
@ECHO %__MVNW_ERROR__% >&2
@SET PSModulePath=%__MVNW_PSMODULEP_SAVE%
@EXIT /B 1

:end
@SET PSModulePath=%__MVNW_PSMODULEP_SAVE%
