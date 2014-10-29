#!/bin/sh
GIT_TAG=$(git describe --tags --match "v*")

# Remove the "v" at the beginning
FULL_VERSION=${GIT_TAG#v}

# Remove anything after a "-"
FULL_VERSION=${FULL_VERSION%%-*}

MAJOR=${FULL_VERSION%%.*}
BUILD=${FULL_VERSION##*.}

MINOR_PATCH_BUILD=${FULL_VERSION#*.}
MINOR=${MINOR_PATCH_BUILD%%.*}

PATCH_BUILD=${MINOR_PATCH_BUILD%.*}
PATCH=${PATCH_BUILD##*.}

VERSION_CODE=$((${MAJOR} * 1000000 + ${MINOR} * 10000 + ${PATCH} * 100 + ${BUILD}))
echo ${VERSION_CODE}
