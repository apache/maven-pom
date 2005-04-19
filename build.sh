#!/bin/bash

set -e
set -x

m2 -N install
(cd continuum-model && m2 install)
(cd continuum-core && m2 install)
(cd continuum-web && m2 install)
(cd continuum-xmlroc && m2 install)
(cd continuum-plexus-application && sh build.sh)
