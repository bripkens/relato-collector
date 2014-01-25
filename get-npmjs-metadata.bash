#!/bin/env bash

curl http://registry.npmjs.org/-/all | \
  python -m json.tool > \
  all-formatted.json

# from the file all-formatted.json, make a new file
# called package_names.json that only contains an
# array of project names.
node --eval "require('fs').writeFile('package_names.json', JSON.stringify(Object.keys(require('./all-formatted.json'))));"
