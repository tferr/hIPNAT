#!/usr/bin/env sh
set -e

# Details of update site. NB: NEUROANAT_UPLOAD_PASS has been encrypted locally
# as per https://docs.travis-ci.com/user/environment-variables/
export UPDATE_SITE="Neuroanatomy"
export URL="http://sites.imagej.net/$UPDATE_SITE/"

# Paths:
export IJ_PATH="$HOME/Fiji.app"
export IJ_LAUNCHER="$IJ_PATH/ImageJ-linux64"
export PATH="$IJ_PATH:$PATH"

# Install IJ
mkdir -p $IJ_PATH/
cd $HOME/
wget --no-check-certificate https://downloads.imagej.net/fiji/latest/fiji-linux64.zip
unzip fiji-linux64.zip
$IJ_LAUNCHER --update update-force-pristine

# Install artifact
cd $TRAVIS_BUILD_DIR/
mvn clean install -Dimagej.app.directory=$IJ_PATH -Ddelete.other.versions=true

# Deploy if release version
VERSION=`mvn help:evaluate -Dexpression=project.version | grep -e '^[^\[]'`

case $VERSION in
  *SNAPSHOT*)
    echo "v$VERSION: skipping SNAPSHOT upload to $URL"
    ;;
  *)
    echo "Uploading v$VERSION to $URL..."
    $IJ_LAUNCHER --update edit-update-site $UPDATE_SITE $URL "webdav:$UPDATE_SITE:$NEUROANAT_UPLOAD_PASS" .
    $IJ_LAUNCHER --update upload --update-site $UPDATE_SITE --force-shadow plugins/hIPNAT_.jar
    ;;
esac
