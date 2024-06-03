# Overview

`smc-upgrader` upgrades an Elastic Path Self-Managed Commerce codebase to a given version.

It has the following benefits:

* Ensures the upgrade is performed via the approach recommended by Elastic Path.
* Can upgrade from and to any version of Elastic Path Self-Managed Commerce.
* Reconciles conflicts caused by the presence of Elastic Path post-release patches.

# Installation

The following section describes how to install and build `smc-upgrader`.

## Prerequisites

To successfully install and use `smc-upgrader`, you will need the `java` command available on the PATH (Java 8 JDK):

* Currently, `smc-upgrader` has only been tested with Java 8.
* `smc-upgrader` has primarily been tested on macOS and Linux, but should work on most platforms with a supported JDK.

## Homebrew installation

1. Tap the `smc-upgrader`'s formula repository:

    ```
    brew tap elasticpath/smc-upgrader
    ```

1. Install `smc-upgrader`:

    ```
    brew install smc-upgrader
    ```

1. Validate the installation by checking the version:
    ```
    smc-upgrader --help
    ```

## Binary Installation

1. Go to [`smc-upgrader` releases](https://github.com/elasticpath/smc-upgrader/releases) and check for currently available releases.
1. Download the required zip file and place it into a folder, such as `~/tools/smc-upgrader`.
1. Extract the downloaded file:

    ```
    unzip smc-upgrader-1.0.0.zip
    ```

1. Set up alias/shortcut:
    1. On a `*nix` running bash, including Mac, create an alias in your terminal.

    **Note**: This can also be made for permanent use, by adding it to your `~/.bash_profile`.

    ```
    alias smc-upgrader='java -jar ~/tools/smc-upgrader/smc-upgrader-1.0.0-SNAPSHOT-jar-with-dependencies.jar'
    ```

    1. On Windows you will likely want to create a `smc-upgrader.cmd` file on your PATH that looks like this:

    ```
    @echo off
    set SMC_UPGRADER_JAR=C:\path\to\smc-upgrader\smc-upgrader-1.0.0-SNAPSHOT-jar-with-dependencies.jar
    java -jar "%SMC_UPGRADER_JAR%" %*
    ```

1. Execute `smc-upgrader --help` to verify the installation.

## Build from source

1. Clone and build the project source as shown in the following example:

    ```
    cd ~/git
    git clone git@github.com:elasticpath/smc-upgrader.git
    cd smc-upgrader
    ./smc-upgraderw clean install
    ```

1. Set up alias/shortcut:
    1. On a `*nix` running bash, including Mac, create an alias in your terminal.

   **Note**: This can also be made for permanent use, by adding it to your `~/.bash_profile`.

    ```
    alias smc-upgrader='java -jar ~/git/smc-upgrader/target/smc-upgrader-1.0.0-SNAPSHOT-jar-with-dependencies.jar'
    ```

    2. On Windows you will likely want to create a `smc-upgrader.cmd` file on your PATH that looks like this:
    ```
    @echo off
    set SMC_UPGRADER_JAR=C:\path\to\git\smc-upgrader\target\smc-upgrader-1.0.0-SNAPSHOT-jar-with-dependencies.jar
    java -jar "%SMC_UPGRADER_JAR%" %*
    ```

1. Execute `smc-upgrader --help` to verify the installation.

# Usage and Examples

The following section describes the different usage and examples for `smc-upgrader`:

## Command Line Options

```text
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

## Usage Examples

### Setup

Before running the application for the first time, ensure the Elastic Path Self-Managed Commerce repository has been added to the git repository as a remote:

```
git remote add smc-upgrades git@code.elasticpath.com:ep-commerce/ep-commerce.git
```

For best results, update the local git repository with the latest changes from the upstream repository before each time the application is run:

```
git fetch --all
```

### Upgrading

The primary usage for `smc-upgrader` is to upgrade an existing codebase to a specified version, performing a merge, resolving conflicts, and verifying the accuracy of git's merge strategy:

```
smc-upgrader 8.5.x
```

Once you have finished resolving all conflicts, you can complete the merge by running:

```
git merge --continue
```

Alternately, you can cancel the merge with:

```
git merge --abort
```

It may be necessary to skip the git merge phase of the operation. This can be useful in cases where the git history of the repository does not include a common ancestor for the upstream changes, e.g. if the repository was initialized via a .zip release package:

```
smc-upgrader --no-do-merge 8.5.x
```

### Demonstration

![SMC Upgrader usage demonstration](smc-upgrader.gif)
