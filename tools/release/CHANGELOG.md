## [2.6.1](https://github.com/th0r88/grimmory/compare/v2.6.0...v2.6.1) (2026-08-10)

### Bug Fixes

* **email:** declare correct media type for book attachments ([45d90de](https://github.com/th0r88/grimmory/commit/45d90de9ec4f2ee25277425938703cf9fc74cceb))
* **email:** sanitise risky characters from email attachment filenames ([466650f](https://github.com/th0r88/grimmory/commit/466650f1c704e5c280135342543a4c84e63afc25))
* **email:** strip Unicode control and format characters from attachment filenames ([fd5ccc0](https://github.com/th0r88/grimmory/commit/fd5ccc05dc7850ab5e85547b8245515a634f56d9))
* **email:** treat leading-dot filenames as having an empty base name ([68bcfd1](https://github.com/th0r88/grimmory/commit/68bcfd155f7e491c7d437a9e6059e0d16c8180dd))
* **email:** use locale-independent extension matching in BookFileExtension ([ccb4f0f](https://github.com/th0r88/grimmory/commit/ccb4f0f1e58f78b8aff4bb82b7721f6fb9f81b00))

### Tests

* **email:** extend EmailSenderHelperTest with MockitoExtension ([b9a402e](https://github.com/th0r88/grimmory/commit/b9a402e3388c2cfed3cdec6cfb011ae6a1ebfdd5))

## [2.6.0](https://github.com/th0r88/grimmory/compare/v2.5.1...v2.6.0) (2026-08-10)

### Features

* **email:** add recipient owner strings ([ef725f4](https://github.com/th0r88/grimmory/commit/ef725f4c040ea61a893e990a2575967c5539d9b6))
* **email:** add userId to recipient request and pin mapper ownership ([2431af3](https://github.com/th0r88/grimmory/commit/2431af32c639871400392e671bb44389f2847457))
* **email:** let admins assign recipients from settings ([72f6d23](https://github.com/th0r88/grimmory/commit/72f6d2393f0c2579e5fac392845f280df28fb95c))
* **email:** scope recipient service to the resolved owner ([d2c0e3b](https://github.com/th0r88/grimmory/commit/d2c0e3b84c326f9662cde1c6fafbb97cecbd280e))

### Bug Fixes

* **email:** correct email_recipient_v2 unique constraint scope ([1d71cb1](https://github.com/th0r88/grimmory/commit/1d71cb11b985887c0c425d88d07aa6a9f6890052))
* **email:** fall back to sole accessible provider for Quick Send ([11da9d4](https://github.com/th0r88/grimmory/commit/11da9d49ea6e6d403be0adcbbf9275ce12fb85ae))
* **email:** keep ownerUsername off responses that never populate it ([d51c597](https://github.com/th0r88/grimmory/commit/d51c597b1a6766deba2d3cb97c6a21b0c0a2fc54))
* **email:** let userId win over scopeAll in the recipient list call ([0998988](https://github.com/th0r88/grimmory/commit/0998988b4b41ebeca352c4907291b2d199962f1c))
* **email:** scope default-promotion on delete to the owning user ([c8e8352](https://github.com/th0r88/grimmory/commit/c8e83528a965936582b11f1ca0a145000e4654df))
* **email:** scope first-recipient default to the owning user ([df0a640](https://github.com/th0r88/grimmory/commit/df0a640590dcc4a5bb3a25e2f08748a5f2c0559d))

### CI

* **release:** make semantic-release run from develop ([c423c51](https://github.com/th0r88/grimmory/commit/c423c51b7e07d21fcef6c326db32c01e8274685c))

### Tests

* **email:** cover admin recipient assignment ([1f7afad](https://github.com/th0r88/grimmory/commit/1f7afade780bad3b143195977dfcd8cb274accd8))
* **email:** cover admin recipient assignment in the UI ([ddd4990](https://github.com/th0r88/grimmory/commit/ddd49903823a4d98eeea9dc47a0bc823158b475e))
