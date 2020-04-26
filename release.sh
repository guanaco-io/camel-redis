#!/bin/bash

# stop on error
set -e

# log error and exit script
function fail {
  echo
  echo "ERROR -- $1"
  exit -1
}

function step {
  echo
  echo $1
  echo "---"
}

# check arguments
if [ $# != 1 ]; then
  echo "Usage: $0 <release version>"
  fail "Error: no <release version> specified"
fi

cd $(dirname $0)
BASEDIR=$(pwd)

ORIGINAL_BRANCH=$(git rev-parse --symbolic-full-name --abbrev-ref HEAD)
VERSION=$1
TAG=release-$VERSION

step "Preparing to release version '$VERSION' from branch '$ORIGINAL_BRANCH'"

# update the codebase
git pull --rebase
git fetch --tags

# preflight checks
if [[ $(git diff --shortstat 2> /dev/null | tail -n1) != "" ]]; then
  fail "Uncommitted changes in git repository - aborting the release"
fi
if [[ $(git status --porcelain 2>/dev/null| grep "^??" | wc -l) > 0 ]]; then
  fail "Untracked files in git repository - aborting the release"
fi
if [[ $(git tag --list $TAG ) ]]; then
  fail "Tag '$TAG' already exists - aborting the release"
fi


RELEASE_BRANCH="release-$VERSION-from-$ORIGINAL_BRANCH"

step "Creating release branch $RELEASE_BRANCH"
if [ $(git branch --list $RELEASE_BRANCH) ]; then
   echo "Branch name $RELEASE_BRANCH already exists - deleting the existing branch"
   git branch -D $RELEASE_BRANCH
fi
git checkout -b $RELEASE_BRANCH $ORIGINAL_BRANCH

step "Update versions in build.sbt to $VERSION"
sed -E -i.bak 's/(version[[:space:]]*\:\=[[:space:]]*)\"(.*)\"/\1"'${VERSION}'"/g' build.sbt

step "Committing changes and creating $TAG"
git add .
git commit -m "[release] Update POMs for release - version $VERSION"
git tag $TAG -m "Release $VERSION"

step "Pushing tag '$TAG' to remote 'origin' and deleting release branch"
git push origin $TAG
git checkout $ORIGINAL_BRANCH
git branch -D $RELEASE_BRANCH

step "Done - tag $TAG has been created"
echo "What's next?"
echo "- check out the tag with 'git checkout $TAG'"
echo "- run the build and test the release locally"
echo "- update the release notes at https://github.com/guanaco-io/camel-redis/releases/tag/$TAG"
