# Changelog

## [0.3.0](https://github.com/lezli01/hotrulez/compare/v0.2.0...v0.3.0) (2026-06-26)


### Features

* add Firestore Rules diagnostics (annotator + inspections) ([8f18b54](https://github.com/lezli01/hotrulez/commit/8f18b54c35a6ba97b7defaf2f121fcba784c3e4b))
* add Grammar-Kit + JFlex grammar for Firestore Rules ([a974202](https://github.com/lezli01/hotrulez/commit/a97420228c638c111c88606ba9d0fa5a64072ea2))


### Bug Fixes

* address review findings in formatter and diagnostics ([a87397c](https://github.com/lezli01/hotrulez/commit/a87397c1a596761e544eb999757bdf23ba6198d0))
* align generated parser integrations ([bf7fd42](https://github.com/lezli01/hotrulez/commit/bf7fd4272e5d06f189f5ca602af157488ad52c22))
* **diagnostics:** correct false-positive and false-negative warnings ([7c1539d](https://github.com/lezli01/hotrulez/commit/7c1539d4313a7263e9cca046e4bf11e7b4a8b8bf))
* **diagnostics:** handle default rules version semantics ([38daa05](https://github.com/lezli01/hotrulez/commit/38daa05c8537d71f21d191c8adc06060964c9ce5))
* **diagnostics:** stop the recursive-wildcard warning stacking on the placement error ([febd918](https://github.com/lezli01/hotrulez/commit/febd918182a595cef17fd4791d8c25ac1bed8c4d))
* **diagnostics:** validate rules_version value ([58d6498](https://github.com/lezli01/hotrulez/commit/58d6498197ef52cbe38e3c1a5cd748d418451b5c))
* **formatter:** hang new lines in literals and argument lists to match reformat ([04f5ea5](https://github.com/lezli01/hotrulez/commit/04f5ea5d1fad3f47d1af2abe2789b59ddfac0bbf))
* harden Firestore rules parsing and diagnostics ([5198987](https://github.com/lezli01/hotrulez/commit/51989877a6e5a72cfde73af70cac9d778fca9e44))
* **lexer:** align highlighting lexer with the JFlex parser lexer ([34a5bed](https://github.com/lezli01/hotrulez/commit/34a5bed34e5d6da74dc328e0327b749bfa59b151))
* **lexer:** reject backtick as an invalid character ([cdd1426](https://github.com/lezli01/hotrulez/commit/cdd142664fffd53da7a91d60a9cbc483a01e567c))
* **parser:** recover blocks and path literals ([54d7302](https://github.com/lezli01/hotrulez/commit/54d7302807b46b4af333cc0eb544961f52b8590d))
* **parser:** recover from malformed top-level and function-body syntax ([d180d2f](https://github.com/lezli01/hotrulez/commit/d180d2fc6c2df15e98b6692f0610ec8880285a11))
* **parser:** tolerate a blockless service while editing ([eb55b3b](https://github.com/lezli01/hotrulez/commit/eb55b3b94970f552bbe92aeae97513d6a6bb1d53))
* restore formatter indent and service-name highlighting ([47b33e3](https://github.com/lezli01/hotrulez/commit/47b33e3408fb5d85e5b4427602a48f4c4e69c1a0))

## [0.2.0](https://github.com/lezli01/hotrulez/compare/v0.1.0...v0.2.0) (2026-06-24)


### Features

* cover ternary, the is operator, and built-in types/namespaces ([baf0231](https://github.com/lezli01/hotrulez/commit/baf0231bd96618de22e458ee86a052a762e20533))
* enhance Firestore Rules syntax highlighting and formatting ([c35045b](https://github.com/lezli01/hotrulez/commit/c35045b2e017a21d6c32a7d36d09bf5dc8da68fe))
* enrich Firestore Rules syntax highlighting ([efa6b8a](https://github.com/lezli01/hotrulez/commit/efa6b8a1cbbe38e62b6ae7ed68207d9c31138859))
* format path expressions and $() interpolation in conditions ([00030e5](https://github.com/lezli01/hotrulez/commit/00030e5cf445a2b6663331787e4af11b3d053fd4))
* hang-indent chained-call and map-literal continuations ([b3cde1a](https://github.com/lezli01/hotrulez/commit/b3cde1a3030d3c8312d47c377da6379685a728ec))
* highlight the let keyword ([b17d6b8](https://github.com/lezli01/hotrulez/commit/b17d6b8bfb06081e8f98d9cdab6ad274d3885d5e))
* indent multi-line expressions and keep grouping-paren spacing ([3823bd0](https://github.com/lezli01/hotrulez/commit/3823bd04bf88dc03cf7220169c815a57874c5d76))
* separate Firestore Rules block members with blank lines ([2ac6d9a](https://github.com/lezli01/hotrulez/commit/2ac6d9a6a60a27a637574df18e44196706097259))


### Bug Fixes

* prevent parser hang on map literals and stray braces ([01ad410](https://github.com/lezli01/hotrulez/commit/01ad4104065c3161e3ca300f25065cc0899d4a6d))

## 0.1.0 (2026-06-23)


### Features

* add firestore rules formatter ([630d9b6](https://github.com/lezli01/hotrulez/commit/630d9b6baeeaf3863ea14fc15b9f0029110a747d))
* scaffold plugin and syntax highlighting ([2b665e2](https://github.com/lezli01/hotrulez/commit/2b665e23bb8374bdf133fcb5891b15636ea86e88))
