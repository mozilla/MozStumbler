#!/usr/bin/env python

import sys
import os
import os.path

BUILD_PATH = '/home/ec2-user/MozStumbler/android/build/outputs/apk'
for file in os.listdir(BUILD_PATH):
    fname = os.path.join(BUILD_PATH, file)
    print 'removing: [%s]' % fname
    os.unlink(fname)

props = {}
for line in open('android/properties/private-%s.properties' % sys.argv[-1]):
    if len(line.strip()) == 0:
        continue
    if line[0] == '#':
        continue
    k, v = line.strip().split('=')
    props[k] = v
assert 'MapAPIKey' in props, 'MapAPIKey is ok'
assert 'MozAPIKey' in props, 'MozAPIKey is ok'
