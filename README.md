# Overview

`smc-upgrader` upgrades an Elastic Path Self-Managed Commerce codebase to a given version.

It has the following benefits:

* Ensures the upgrade is performed via the approach recommended by Elastic Path.
* Can upgrade from and to any version of Elastic Path Self-Managed Commerce.
* Supports consuming all latest patches for your current version without performing a full version upgrade.
* Reconciles conflicts caused by the presence of Elastic Path post-release patches.
* AI Assist Mode uses Claude Code to guide you through virtually the entire upgrade process -- from merging and conflict resolution to compilation failures, test failures, schema updates, and more.

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
    unzip smc-upgrader-2.0.0.zip
    ```

1. Set up alias/shortcut:
    1. On a `*nix` running bash, including Mac, create an alias in your terminal.

    **Note**: This can also be made for permanent use, by adding it to your `~/.bash_profile`.

    ```
    alias smc-upgrader='java -jar ~/tools/smc-upgrader/smc-upgrader-2.0.0-jar-with-dependencies.jar'
    ```

    1. On Windows you will likely want to create a `smc-upgrader.cmd` file on your PATH that looks like this:

    ```
    @echo off
    set SMC_UPGRADER_JAR=C:\path\to\smc-upgrader\smc-upgrader-2.0.0-jar-with-dependencies.jar
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
    alias smc-upgrader='java -jar ~/git/smc-upgrader/target/smc-upgrader-2.0.0-SNAPSHOT-jar-with-dependencies.jar'
    ```

    2. On Windows you will likely want to create a `smc-upgrader.cmd` file on your PATH that looks like this:
    ```
    @echo off
    set SMC_UPGRADER_JAR=C:\path\to\git\smc-upgrader\target\smc-upgrader-2.0.0-SNAPSHOT-jar-with-dependencies.jar
    java -jar "%SMC_UPGRADER_JAR%" %*
    ```

1. Execute `smc-upgrader --help` to verify the installation.

# Command Line Options

```text
Usage: smc-upgrader [-dfhmprvV] [--ai:continue] [--ai:skip-permissions] [--ai:
                    start] [--[no-]clean-working-directory-check]
                    [-C=<workingDir>] [-u=<upstreamRemoteRepositoryUrl>]
                    [<version>]
Utility to apply Elastic Path Self-Managed Commerce updates to a codebase.
      [<version>]            The version of Elastic Path Self-Managed Commerce
                               to upgrade to. Optional when using --ai:start or
                               --ai:continue.
      --ai:continue          Continue AI-assisted upgrade from saved plan.
      --ai:skip-permissions  Skip permission prompts when invoking Claude Code
                               (passes --dangerously-skip-permissions).
      --ai:start             Start AI-assisted upgrade mode and generate
                               upgrade plan. Requires version parameter.
  -C=<workingDir>            The working directory containing the git repo to
                               be upgraded. Defaults to the current working
                               directory.
      --[no-]clean-working-directory-check
                             Indicates whether to do a clean working directory
                               check. Enabled by default.
  -d, --[no-]resolve-diffs   Indicates whether to reconcile diffs between the
                               merged branch and the upstream contents. Enabled
                               by default.
  -f, --[no-]fetch           Indicates whether to fetch the latest updates from
                               the remote. Enabled by default.
  -h, --help                 Show this help message and exit.
  -m, --[no-]merge           Indicates whether to perform a merge. Enabled by
                               default.
  -p, --[no-]revert-patches  Indicates whether to revert patches before
                               merging. Enabled by default.
  -r, --[no-]resolve-conflicts
                             Indicates whether to resolve merge conflicts.
                               Enabled by default.
  -u, --upstream-repository-url=<upstreamRemoteRepositoryUrl>
                             The URL of the upstream repository containing
                               upgrade commits.
  -v, --verbose              Enables debug logging.
  -V, --version              Print version information and exit.
```

# Standard Mode Usage

The following sections describe how to use `smc-upgrader` in standard mode (without the `--ai` flags).

## Setup

Before running the application for the first time, ensure the Elastic Path Self-Managed Commerce repository has been added to the git repository as a remote:

```
git remote add smc-upgrades git@code.elasticpath.com:ep-commerce/ep-commerce.git
```

## Upgrading

The primary usage for `smc-upgrader` is to upgrade an existing codebase to a specified release version by executing these steps:

1. Fetch step: Fetches the latest updates from the `release/<version>` branch of the `code.elasticpath.com` repository.
2. Merge step: Merges the `release/<version>` branch of the `code.elasticpath.com` repository into the current branch.
3. Resolve conflicts step: Iterates across each file with conflicts, checking to see if the file contents of the latest commit matches a commit in a `code.elasticpath.com` branch. If it does, resolves the conflict using the `code.elasticpath.com` version of the file.
4. Resolve diffs step: Iterates across each file in the repo, checking to see if the file contents of the latest commit matches a commit in a `code.elasticpath.com` branch. If it does, overwrites the file contents with the `code.elasticpath.com` file contents.

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

## Demonstration

![SMC Upgrader standard mode demonstration](smc-upgrader.gif)

# AI Assist Mode Usage

The following sections describe how to use `smc-upgrader` in AI assist mode (with the `--ai` flags).

> **Important:** AI Assist Mode is not a fully automated process. Experienced developers must actively guide Claude Code throughout the upgrade and carefully review all changes it makes. Claude can make mistakes -- for example, it may replace important customer customizations with standard platform functionality rather than correctly merging the two. Do not treat any AI-generated change as correct without review.
>
> AI Assist Mode works best when the codebase has extensive automated regression tests that cover all custom functionality. These tests are the primary mechanism for detecting mistakes. If comprehensive test coverage is not present, AI Assist Mode may not be able to complete the upgrade successfully, and manual review effort will increase significantly.
>
> **Note on usage fees:** Claude Code usage is billed based on the number of tokens processed. Upgrading a large codebase may result in significant Claude API charges. Review [Anthropic's pricing](https://www.anthropic.com/pricing) before proceeding.

## Setup

Before running the application for the first time, ensure the Elastic Path Self-Managed Commerce repository has been added to the git repository as a remote:

```shell
git remote add smc-upgrades git@code.elasticpath.com:ep-commerce/ep-commerce.git
```

> **Requirement:** AI Assist Mode requires [Claude Code](https://code.claude.com/docs/en/quickstart) to be installed and a [paid Claude plan](https://claude.com/pricing). Install Claude Code and sign up for a paid plan before proceeding.

## AI Assist Start

AI Assist Mode can be used to help with version upgrades or to consume all the latest patches for your current version. The tool generates a plan containing all required steps, which are executed one at a time. Steps cover the full upgrade workflow, including:

* Merging the upgraded platform codebase into your branch
* Resolving Git merge conflicts
* Fixing Maven validation and compilation failures
* Resolving unit test and integration test failures
* Handling schema update issues
* Resolving Cucumber test failures
* Fixing server startup problems

To start an AI-assisted upgrade, run:

```shell
smc-upgrader --ai:start <version>
```

Where `<version>` is the **target** version you want to upgrade to, such as `8.7.x`.

To consume all latest patches on your **current** version without performing a full version upgrade, pass your current version instead:

```shell
smc-upgrader --ai:start <current-version>
```

This step will generate an upgrade plan file named `smc-upgrader-plan.md` and commit it to Git with a commit message starting with `Generated upgrade plan`.

We recommend that you review the upgrade plan before continuing. You can add or remove steps, change prompts, or make any other required changes to the plan, which will be read by the tool for all subsequent operations.

## AI Assist Continue

Once you are ready to continue, run:

```shell
smc-upgrader --ai:continue
```

The tool will read the upgrade plan from `smc-upgrader-plan.md` and check to see if there are any steps in the `in progress` state. If it finds one, it will prompt the user to decide what they want to do next, as in the following example:

```text
INFO : Next step: Resolve all unit test failures
INFO :   Tool: claude
INFO :   Validation command: mvn clean install -DskipITests -DskipCucumberTests -T0.75C
INFO :
INFO : What would you like to do?
INFO :   [E] Execute this step
INFO :   [V] Verify that this step is complete
INFO :   [M] Mark this step as complete
INFO :   [X] Exit
```

If you choose `E`, then Claude Code will be executed again with the prompt specified in the plan.
If you choose `V`, then the validation command will be executed (this may take a few minutes), and if successful, the step will be marked as `complete`, and the tool will exit.
If you choose `M`, the step will be marked as `complete`, and the tool will exit.
If you choose `X`, the tool will just exit without doing anything else.

If there are no steps `in progress`, the tool will find the first `not started` step, change it to `in progress`, and execute the step automatically.

By default, the first step is to start merging from the Self-Managed Commerce repository. This step will be completed automatically and does not involve AI. For all other steps, Claude Code will be invoked with the prompt from the plan.

You can interact with Claude Code normally, providing guidance or correcting mistakes. Claude Code may also ask for advice when it's not sure about the best way to proceed. When Claude Code appears to be done, type `/exit` to exit the interactive Claude Code tool.

When Claude Code exits, you will be prompted to decide what you want to do:

```text
INFO : Claude Code completed successfully.
INFO :
INFO : Was this step successfully completed?
INFO :   [Y/M] Mark this step as complete
INFO :   [V] Verify that this step is complete
INFO :   [N/X] Exit
INFO :
```

If you choose `Y` or `M`, the step will be marked as `complete`, and the tool will exit.
If you choose `V`, then the validation command will be executed (this may take a few minutes), and if successful, the step will be marked as `complete`, and the tool will exit.
If you choose `N` or `X`, the tool will just exit without doing anything else.

You can then run `smc-upgrader --ai:continue` again to continue the current step or move on to the next step. Keep running this command until all steps are completed.

## Troubleshooting

### Git merge failed. Usually this means that Git could not find a common ancestor commit between your branch and the Self Managed Commerce release branch.

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

### SSH authentication fails during fetch with "No keys found in identity" or "Cannot log in"

`smc-upgrader` uses JGit for Git operations. JGit's built-in SSH client (Apache MINA SSHD) has the following limitations:

* SSH keys **must not** have a passphrase - JGit cannot decrypt protected keys.

If you encounter this error, generate an SSH key without a passphrase:

```shell
ssh-keygen -t rsa -b 4096 -N "" -f ~/.ssh/id_rsa_smc
```

Add the public key to your GitLab account at `code.elasticpath.com`, and configure SSH to use it for that host by adding the following to `~/.ssh/config`:

```
Host code.elasticpath.com
    IdentityFile ~/.ssh/id_rsa_smc
    IdentitiesOnly yes
```
