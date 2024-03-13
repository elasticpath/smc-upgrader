# Overview

`smc-upgrader` upgrades an Elastic Path Self-Managed Commerce codebase to a given version.

It has the following benefits:

* Ensures the upgrade is performed via the approach recommended by Elastic Path.
* Can upgrade from and to any version of Elastic Path Self-Managed Commerce.
* Reconciles conflicts caused by the presence of Elastic Path post-release patches.

# Installation

The following section describes how to install and build `smc-upgrade`.

## Prerequisites

To successfully install and use `smc-upgrade`, you will need the `java` command available on the PATH (Java 8 JDK):

* Currently, `smc-upgrade` has only been tested with Java 8.
* `smc-upgrade` has primarily been tested on macOS and Linux, but should work on most platforms with a supported JDK.

## Build from source

1. Clone and build the project source as shown in the following example:

    ```
    cd ~/git
    git clone gitlab.elasticpath.com:commerce-cloud/playground/smc-upgrader.git
    cd smc-upgrader
    ./smc-upgraderw clean install
    ```

1. On a Linux/Mac running bash, add the following to your `~/.bash_profile`:

    ```
    alias smc-upgrader='java -jar ~/git/smc-upgrader/target/smc-upgrader-1.0.0-SNAPSHOT-jar-with-dependencies.jar'
    ```

# Usage and Examples

The following section describes the different usage and examples for `smc-upgrade`.

## Command Line Options

```
 Usage: smc-upgrader [-dhmrvV] [-C=<workingDir>]
                    [-u=<upstreamRemoteRepositoryUrl>] <version>

Utility to apply Elastic Path Self-Managed Commerce updates to a codebase.

  <version>             The version of Elastic Path Self-Managed Commerce to upgrade to.
  -C=<workingDir>       The working directory containing the git repo to be upgraded. Defaults to the current working directory.
  -d, --[no-]do-resolve-diffs
                        Toggles whether to reconcile diffs between the merged branch and the upstream contents. Enabled by default.
  -h, --help            Show this help message and exit.
  -m, --[no-]do-merge   Toggles whether to perform a merge. Enabled by default.
  -r, --[no-]do-resolve-conflicts
                        Toggles whether to resolve merge conflicts. Enabled by default.
  -u, --upstream-repository-url=<upstreamRemoteRepositoryUrl>
                        The URL of the upstream repository containing upgrade commits.
  -v, --verbose         Enables debug logging.
  -V, --version         Print version information and exit.


```

## Basic Examples

- The primary usage for `smc-upgrader` is to upgrade an existing codebase to a specified version, performing a merge, resolving conflicts, and
  verifying the accuracy of git's merge strategy:

  ```
  smc-upgrader 8.5.x
  ``` 

- It may be necessary to skip the git merge phase of the operation. This can be useful in cases where the git history of the repository does not
  include a common ancestor for the upstream changes, e.g. if the repository was initialized via a .zip release package:

  ```
  smc-upgrader --no-do-merge 8.5.x
  ```

## Tips & Tricks



# License

Copyright 2024, Elastic Path Software Inc.
