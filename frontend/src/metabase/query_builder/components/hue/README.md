# Hue Integration

This directory was copied from https://github.com/gethue/hue/tree/master/desktop/core/src/desktop/js/parse/sql/generic

Imports were modified to import related files locally.

This method has the advantage of being very lightweight, but this is NOT the right way to do it. Instead we should try the following solutions :

## Use the npm package gethue
```
yarn add gethue
```

At the moment this fails because the npm package uses two local tarballs :
- file:desktop/core/src/desktop/static/desktop/ext/cuix/cloudera-cuix-core-1.0.6.tgz
- file:desktop/core/src/desktop/static/desktop/ext/cuix/cuix-13.0.4.tgz

The error produced is :
```
error "../../.cache/yarn/v6/npm-gethue-6.0.0-7ab24d8b57a17d145c979807d42fb9476959d1ce-integrity/node_modules/gethue/desktop/core/src/desktop/static/desktop/ext/cuix/cloudera-cuix-core-1.0.6.tgz": Tarball is not in network and can not be located in cache (["/home/louis/.cache/yarn/v6/npm-gethue-6.0.0-7ab24d8b57a17d145c979807d42fb9476959d1ce-integrity/node_modules/gethue/desktop/core/src/desktop/static/desktop/ext/cuix/cloudera-cuix-core-1.0.6.tgz","/home/louis/.cache/yarn/v6/.tmp/d312bece981bb151afad65c3cba6d917/.yarn-tarball.tgz"])
```

## Use the hue repository as a submodule of the metabase repository

```
git submodule add git@github.com:gethue/hue.git
```

And linking the import sqlAutocompleteParser to the local clone repository.