#!/bin/sh

if [ ! -e private.properties ]
then
    echo "You need a private.properties file defined to do a release build."
    return 1
fi

