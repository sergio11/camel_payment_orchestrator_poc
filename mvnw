#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script, version 3.3.2
#
# Optional ENV vars:
# -----------------
#   JAVA_HOME - location of a JDK home dir, required when download maven via java source
#   MVNW_REPOURL - repo url base for downloading maven distribution
#   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
#   MVNW_VERBOSE - true: enable verbose log; debug: trace the mvnw script; others: silence the output
# ----------------------------------------------------------------------------

set -euf
[ "${MVNW_VERBOSE-}" != debug ] || set -x

# OS specific support.
native_path() { printf %s\\n "$1"; }
case "$(uname)" in
CYGWIN* | MINGW*)
  [ -z "${JAVA_HOME-}" ] || JAVA_HOME="$(cygpath --unix "$JAVA_HOME")"
  native_path() { cygpath --path --windows "$1"; }
  ;;
esac

# set JAVA_HOME to default if not set
if [ -z "${JAVA_HOME-}" ]; then
  if [ -x /usr/libexec/java_home ]; then
    JAVA_HOME="$(/usr/libexec/java_home)" || :
  fi
fi

# Determine the Java command to use to start the JVM.
if [ -n "${JAVA_HOME-}" ]; then
  if [ -x "$JAVA_HOME/jre/sh/java" ]; then
    # IBM's JDK on AIX uses strange locations for the executables
    JAVACMD="$JAVA_HOME/jre/sh/java"
  else
    JAVACMD="$JAVA_HOME/bin/java"
  fi
  if [ ! -x "$JAVACMD" ]; then
    echo "The JAVA_HOME environment variable is not defined correctly, so Apache Maven Wrapper cannot run." >&2
    echo "JAVA_HOME is set to \"$JAVA_HOME\", but \"\$JAVA_HOME/bin/java\" does not exist." >&2
    exit 1
  fi
else
  JAVACMD="$(\unset -f command 2>/dev/null; \command -v java)" || :
  if [ ! -x "${JAVACMD-}" ]; then
    echo "The java binary is not found in PATH, and JAVA_HOME environment variable is not defined, so Apache Maven Wrapper cannot run." >&2
    exit 1
  fi
fi

# hash string like 256bit to a short 8 character hash
hash_string() {
  str="${1:-}" h=0
  while [ -n "$str" ]; do
    char="${str%"${str#?}"}"
    h=$(((h * 31 + $(LC_CTYPE=C printf %d "'$char")) % 4294967296))
    str="${str#?}"
  done
  printf %x\\n $h
}

verbose() { :; }
[ "${MVNW_VERBOSE-}" != true ] || verbose() { printf %s\\n "${1-}"; }

die() {
  printf %s\\n "$1" >&2
  exit 1
}

trim() {
  # MWRAPPER-139:
  #   Trims trailing carriage returns and whitespace
  printf "%s" "${1}" | tr -d '[:space:]'
}

# parse distributionUrl and optional distributionSha256Sum, requires .teledata/teledata-wrapper.properties
while IFS="=" read -r key value; do
  case "${key-}" in
  distributionUrl) distributionUrl=$(trim "${value-}") ;;
  distributionSha256Sum) distributionSha256Sum=$(trim "${value-}") ;;
  esac
done <"${0%/*}/.mvn/wrapper/maven-wrapper.properties"

[ -n "${distributionUrl-}" ] || die "cannot read distributionUrl property in ${0%/*}/.mvn/wrapper/maven-wrapper.properties"

case "${distributionUrl##*/}" in
maven-mvnd-*bin.*)
  MVN_CMD=mvnd.sh _MVNW_REPO_PATTERN=/maven/mvnd/
  case "${PROCESSOR_ARCHITECTURE-}${PROCESSOR_ARCHITEW6432-}:$(uname -a)" in
  *AMD64:CYGWIN* | *AMD64:MINGW*) distributionPlatform=windows-amd64 ;;
  :Darwin*x86_64) distributionPlatform=darwin-amd64 ;;
  :Darwin*arm64) distributionPlatform=darwin-aarch64 ;;
  :Linux*aarch64*) distributionPlatform=linux-aarch64 ;;
  :*) distributionPlatform=linux-amd64 ;;
  esac
  distributionUrl="${distributionUrl%-bin.*}-${distributionPlatform}.zip"
  ;;
*)
  MVN_CMD=mvn _MVNW_REPO_PATTERN=/org/apache/maven/
  ;;
esac

# apply MVNW_REPOURL and calculate MAVEN_HOME
# maven home pattern: ~/.m2/wrapper/dists/{apache-maven-<ver>,maven-mvnd-<ver>-<platform>}/<hash>
[ -z "${MVNW_REPOURL-}" ] || distributionUrl="$MVNW_REPOURL$_MVNW_REPO_PATTERN${distributionUrl#*"$_MVNW_REPO_PATTERN"}"
distributionUrlName="${distributionUrl##*/}"
distributionUrlNameMain="${distributionUrlName%%.*}"
distributionUrlHash=$(hash_string "$distributionUrl")
MAVEN_HOME="${MAVEN_USER_HOME:-${HOME}/.m2}/wrapper/dists/${distributionUrlNameMain}/${distributionUrlHash}"

exec_maven() {
  unset MVNW_VERBOSE MVNW_USERNAME MVNW_PASSWORD MVNW_REPOURL || :
  exec "$MAVEN_HOME/bin/$MVN_CMD" "$@" || die "cannot exec $MAVEN_HOME/bin/$MVN_CMD"
}

if [ -d "$MAVEN_HOME" ]; then
  verbose "found existing MAVEN_HOME at $MAVEN_HOME"
  exec_maven "$@"
fi

case "${distributionUrl-}" in
*?-bin.zip | *?maven-mvnd-?*-?*.zip) ;;
*) die "distributionUrl is not valid, must match *-bin.zip or maven-mvnd-*.zip, but found '${distributionUrl-}'" ;;
esac

# prepare tmp dir
if TMP_DOWNLOAD_DIR="$(mktemp -d)" && [ -d "$TMP_DOWNLOAD_DIR" ]; then
  clean() { rm -rf -- "$TMP_DOWNLOAD_DIR"; }
  trap clean HUP INT TERM EXIT
else
  die "cannot create temp dir"
fi

mkdir -p -- "${MAVEN_HOME%/*}"

# Download and Install Apache Maven
verbose "Couldn't find MAVEN_HOME, downloading and installing it ..."
verbose "Downloading from: $distributionUrl"
verbose "Downloading to: $TMP_DOWNLOAD_DIR/$distributionUrlName"

# Use wget/curl/java to download
if [ "${MVNW_VERBOSE-}" = true ]; then
  if command -v wget > /dev/null; then
    verbose "Found wget ... using wget"
    wget ${MVNW_USERNAME:+"--user=$MVNW_USERNAME"} ${MVNW_PASSWORD:+"--password=$MVNW_PASSWORD"} "$distributionUrl" -O "$TMP_DOWNLOAD_DIR/$distributionUrlName" || die "wget: Failed to fetch $distributionUrl"
  elif command -v curl > /dev/null; then
    verbose "Found curl ... using curl"
    curl ${MVNW_USERNAME:+"-u$MVNW_USERNAME:$MVNW_PASSWORD"} -o "$TMP_DOWNLOAD_DIR/$distributionUrlName" "$distributionUrl" -f -L || die "curl: Failed to fetch $distributionUrl"
  else
    verbose "Falling back to use Java to download"
    javaSource="$TMP_DOWNLOAD_DIR/Downloader.java"
    targetZip="$TMP_DOWNLOAD_DIR/$distributionUrlName"
    cat >"$javaSource" <<-END
public class Downloader extends java.net.Authenticator
{
  protected java.net.PasswordAuthentication getPasswordAuthentication()
  {
    return new java.net.PasswordAuthentication( System.getenv("MVNW_USERNAME"), System.getenv("MVNW_PASSWORD").toCharArray() );
  }
  public static void main( String[] args ) throws Exception
  {
    setDefault( new Downloader() );
    java.nio.file.Files.copy( java.net.URI.create( args[0] ).toURL().openStream(), java.nio.file.Paths.get( args[1] ).toAbsolutePath().normalize() );
  }
}
END
    "$JAVACMD" -cp "$javaSource" Downloader "$distributionUrl" "$targetZip"
  fi
else
  if command -v wget > /dev/null; then
    wget ${MVNW_USERNAME:+"--user=$MVNW_USERNAME"} ${MVNW_PASSWORD:+"--password=$MVNW_PASSWORD"} "$distributionUrl" -O "$TMP_DOWNLOAD_DIR/$distributionUrlName" -q || die "wget: Failed to fetch $distributionUrl"
  elif command -v curl > /dev/null; then
    curl ${MVNW_USERNAME:+"-u$MVNW_USERNAME:$MVNW_PASSWORD"} -o "$TMP_DOWNLOAD_DIR/$distributionUrlName" "$distributionUrl" -sf -L || die "curl: Failed to fetch $distributionUrl"
  else
    javaSource="$TMP_DOWNLOAD_DIR/Downloader.java"
    targetZip="$TMP_DOWNLOAD_DIR/$distributionUrlName"
    cat >"$javaSource" <<-END
public class Downloader extends java.net.Authenticator
{
  protected java.net.PasswordAuthentication getPasswordAuthentication()
  {
    return new java.net.PasswordAuthentication( System.getenv("MVNW_USERNAME"), System.getenv("MVNW_PASSWORD").toCharArray() );
  }
  public static void main( String[] args ) throws Exception
  {
    setDefault( new Downloader() );
    java.nio.file.Files.copy( java.net.URI.create( args[0] ).toURL().openStream(), java.nio.file.Paths.get( args[1] ).toAbsolutePath().normalize() );
  }
}
END
    "$JAVACMD" -cp "$javaSource" Downloader "$distributionUrl" "$targetZip"
  fi
fi

# If specified, validate the SHA-256 sum of the Maven distribution zip file
if [ -n "${distributionSha256Sum-}" ]; then
  distributionSha256Result=false
  if [ "$distributionSha256Sum" = "$(sha256sum "$TMP_DOWNLOAD_DIR/$distributionUrlName" | cut -d ' ' -f 1)" ]; then
    distributionSha256Result=true
  elif [ "$distributionSha256Sum" = "$(shasum -a 256 "$TMP_DOWNLOAD_DIR/$distributionUrlName" | cut -d ' ' -f 1)" ]; then
    distributionSha256Result=true
  fi
  if [ $distributionSha256Result = false ]; then
    die "error: failed to verify the SHA-256 sum for the Maven distribution"
  fi
fi

# unzip and move
if command -v unzip > /dev/null; then
  unzip ${MVNW_VERBOSE:+-q} "$TMP_DOWNLOAD_DIR/$distributionUrlName" -d "$TMP_DOWNLOAD_DIR" || die "failed to unzip"
else
  tar xzf${MVNW_VERBOSE:+v} "$TMP_DOWNLOAD_DIR/$distributionUrlName" -C "$TMP_DOWNLOAD_DIR" || die "failed to untar"
fi
printf %s\\n "$distributionUrl" >"$TMP_DOWNLOAD_DIR/$distributionUrlNameMain/mvnw.url"
mv -- "$TMP_DOWNLOAD_DIR/$distributionUrlNameMain" "$MAVEN_HOME" || [ -d "$MAVEN_HOME" ] || die "fail to move MAVEN_HOME"

clean || :
exec_maven "$@"
