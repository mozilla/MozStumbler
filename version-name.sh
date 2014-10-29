#!/bin/sh
GIT_TAG=$(git describe --tags --match "v*")

echo ${GIT_TAG}
