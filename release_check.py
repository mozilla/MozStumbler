#!/usr/bin/env python

props = {}
for line in open('private.properties'):
    if len(line.strip()) == 0:
        continue
    k, v = line.strip().split('=')
    props[k] = v
assert 'MapAPIKey' in props, 'MapAPIKey is ok'
assert 'MozAPIKey' in props, 'MozAPIKey is ok'
assert 'ACRA_URI' in props, 'ACRA_URI is ok'
assert 'ACRA_USER' in props, 'ACRA_USER is ok'
assert 'ACRA_PASS' in props, 'ACRA_PASS is ok'
