# Overview

`smc-upgrader` upgrades an Elastic Path Self-Managed Commerce codebase to a given version.

It has the following benefits:

* Ensures the upgrade is performed via the approach recommended by Elastic Path.
* Can upgrade from and to any version of Elastic Path Self-Managed Commerce.
* Reconciles conflicts caused by the presence of Elastic Path post-release patches.

# Installation

The following section describes how to install and build `smc-upgrader`.

## Prerequisites

To successfully install and use `smc-upgrader`, you will need the `java` command available on the PATH (Java 8 JRE or later).

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
Usage: smc-upgrader [-dhmprvV] [--[no-]clean-working-directory-check]
                    [-C=<workingDir>] [-u=<upstreamRemoteRepositoryUrl>]
                    <version>
Utility to apply Elastic Path Self-Managed Commerce updates to a codebase.
      <version>              The version of Elastic Path Self-Managed Commerce
                               to upgrade to.
  -C=<workingDir>            The working directory containing the git repo to
                               be upgraded. Defaults to the current working
                               directory.
      --[no-]clean-working-directory-check
                             Toggles whether to do a clean working directory
                               check. Enabled by default.
  -d, --[no-]resolve-diffs   Toggles whether to reconcile diffs between the
                               merged branch and the upstream contents. Enabled
                               by default.
  -h, --help                 Show this help message and exit.
  -m, --[no-]merge           Toggles whether to perform a merge. Enabled by
                               default.
  -p, --[no-]revert-patches  Toggles whether to revert patches before merging.
                               Enabled by default.
  -r, --[no-]resolve-conflicts
                             Toggles whether to resolve merge conflicts.
                               Enabled by default.
  -u, --upstream-repository-url=<upstreamRemoteRepositoryUrl>
                             The URL of the upstream repository containing
                               upgrade commits.
  -v, --verbose              Enables debug logging.
  -V, --version              Print version information and exit.
```

## Usage Examples

### Setup

Before running the application for the first time, ensure the Elastic Path Self-Managed Commerce repository has been added to the git repository as a remote:

```
git remote add smc-upgrades git@code.elasticpath.com:ep-commerce/ep-commerce.git
```

For best results, update the local git repository with the latest changes from the upstream repository before each time the application is run:

```
git fetch smc-upgrades
```

### Upgrading

The primary usage for `smc-upgrader` is to upgrade an existing codebase to a specified release version by executing these steps:

1. Merge step: Merges the `release/<version>` branch from `code.elasticpath.com` into the current branch.
2. Resolve conflicts step: Iterates across each file with conflicts, checking to see if the file contents of the latest commit matches a commit in a `code.elasticpath.com` branch. If it does, resolves the conflict using the `code.elasticpath.com` version of the file.
3. Resolve diffs step: Iterates across each file in the repo, checking to see if the file contents of the latest commit matches a commit in a `code.elasticpath.com` branch. If it does, overwrites the file contents with the `code.elasticpath.com` file contents.

This can be started by running:

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

If you prefer to start the merge manually, and then only have `smc-upgrader` attempt to resolve conflicts automatically, use this command:

```
smc-upgrader --no-merge 8.5.x
```

### Troubleshooting

#### Git merge failed. Usually this means that Git could not find a common ancestor commit between your branch and the Self Managed Commerce release branch.

If `smc-upgrader` shows this error, it usually means that your Git repository was initialized using a snapshot of the source code, rather than by cloning from `code.elasticpath.com`. This will be the case if your project team started with SMC 7.0.1 or earlier, before the `code.elasticpath.com` public repository was available.

Follow these steps to create a common ancestor in your Git repository:

1. Browse to the [`code.elasticpath.com` repository](https://code.elasticpath.com/ep-commerce/ep-commerce/-/commits/main/?ref_type=HEADS) and make note of the SHA of the commit representing your current version of Self-Managed Commerce. For example, the SHA for SMC 8.5 is `08d434d4b7bc577c0b15f3b600dba4e6dc4a63fd`.
2. Ensure that you have followed the [Setup](#setup) steps and have a terminal window open in your source code folder.
3. Create a temporary branch containing the `code.elasticpath.com` release source code. Replace `${SHA}` with the SHA that you identified in step 1.

```shell
git checkout -b temp-branch ${SHA}
```

4. Switch back to your `main` or `develop` branch:

```shell
git checkout main
```

5. Create a feature branch for the upgrade:

```shell
git checkout -b smc-upgrade
```

6. Merge from the `temp-branch`, but throw away all the changes:

```shell
git merge --allow-unrelated-histories -s ours temp-branch
```

7. Delete the `temp-branch`:

```shell
git branch -D temp-branch
```

8. Follow the [upgrading](#upgrading) steps normally.

You should only have to do this once; future uses of the tool should work without issue.

### Demonstration

![SMC Upgrader usage demonstration](smc-upgrader.gif)
