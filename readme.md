# camel-redis

## Release & deploy

This project uses GitHub Package Repository. In order to deploy a version of the packages, you need to set up a personal access token with `repo` and the three `*:packages` scopes.
Cfr. [here](https://help.github.com/en/github/authenticating-to-github/creating-a-personal-access-token-for-the-command-line) for more information on how to do that.

Afterwards, make sure the access token is available in the `GITHUB_TOKEN` environment variable (we recommend using [direnv](https://direnv.net/)).

Building and deploying a release:

* run the `./release.sh <version>` script to create the release tag
* checkout the release tag: `git checkout release-<version>`
* run the release build to with `sbt clean test publish`
