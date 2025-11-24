# AI Assist Mode Design Document

## Overview

This document outlines the design for an AI-assisted upgrade mode that helps customers complete the Self-Managed Commerce upgrade process with the assistance of Claude Code CLI.

## Goals

- Automate the generation of a structured upgrade plan
- Guide customers through each step of the upgrade process
- Integrate Claude Code CLI for AI-assisted problem resolution
- Support multi-version upgrades with sequential intermediate steps
- Allow customers to customize the upgrade plan

## User Experience

### Starting an AI-Assisted Upgrade

```bash
$ smc-upgrader --ai:start 8.7.x
Detected current version: 8.5.x
Upgrade path: 8.5.x -> 8.6.x -> 8.7.x
Generated upgrade plan: smc-upgrader-plan.md

You can review and customize the plan if needed.

IMPORTANT: This upgrade process requires Claude Code CLI.
Please ensure Claude Code is installed and you have a Claude Pro account.
For installation instructions, visit: https://www.claude.com/product/claude-code

To continue with the upgrade, run:
  smc-upgrader --ai:continue
```

### Starting with Existing Plan

If a plan already exists, the tool will prompt for confirmation:

```bash
$ smc-upgrader --ai:start 8.7.x
WARNING: An upgrade plan already exists at: smc-upgrader-plan.md

This plan may contain customizations or progress from a previous upgrade.
Do you want to overwrite it? [y/N]: n

Keeping existing plan. To continue with the existing upgrade, run:
  smc-upgrader --ai:continue

To use a different plan file name, move or rename the existing plan first.
```

If the customer chooses to overwrite:

```bash
$ smc-upgrader --ai:start 8.7.x
WARNING: An upgrade plan already exists at: smc-upgrader-plan.md

This plan may contain customizations or progress from a previous upgrade.
Do you want to overwrite it? [y/N]: y

Detected current version: 8.5.x
Upgrade path: 8.5.x -> 8.6.x -> 8.7.x
Generated upgrade plan: smc-upgrader-plan.md

You can review and customize the plan if needed.

IMPORTANT: This upgrade process requires Claude Code CLI.
Please ensure Claude Code is installed and you have a Claude Pro account.
For installation instructions, visit: https://www.claude.com/product/claude-code

To continue with the upgrade, run:
  smc-upgrader --ai:continue
```

### Continuing the Upgrade

```bash
$ smc-upgrader --ai:continue
Reading plan from: smc-upgrader-plan.md

Next step: Git merge from 8.5.x to 8.6.x
  Task: Upgrade source from 8.5.x to 8.6.x
  Tool: smc-upgrader
  Status: incomplete

What would you like to do?
  [E] Execute this step
  [C] Check if this step is complete
  [M] Mark this step as complete
  [X] Exit

Your choice: E

Executing merge from 8.5.x to 8.6.x...
Merge completed successfully.
Step marked as complete.

Exiting. Run 'smc-upgrader --ai:continue' to continue with the next step.
```

Note: Steps using the smc-upgrader tool are automatically marked complete after execution since the tool knows whether they succeeded. The customer can now move to the next step:

```bash
$ smc-upgrader --ai:continue
Reading plan from: smc-upgrader-plan.md

Next step: Resolve 8.6.x merge conflicts
  Task: Resolve remaining Git merge conflicts
  Tool: claude
  Validation command: git diff --check
  Status: incomplete

What would you like to do?
  [E] Execute this step
  [C] Check if this step is complete
  [M] Mark this step as complete
  [X] Exit

Your choice: E

Launching Claude Code with upgrade context...
(Claude Code session starts)

Note: Steps using the claude tool are NOT automatically marked complete after execution.
The customer must validate the results and explicitly mark the step complete.
```

After the customer finishes working with Claude Code and exits:

```bash
$ smc-upgrader --ai:continue
Reading plan from: smc-upgrader-plan.md

Next step: Resolve 8.6.x merge conflicts
  Task: Resolve remaining Git merge conflicts
  Tool: claude
  Validation command: git diff --check
  Status: incomplete

What would you like to do?
  [E] Execute this step
  [C] Check if this step is complete
  [M] Mark this step as complete
  [X] Exit

Your choice: C

Running validation command: git diff --check
Validation command exited with code: 0

Validation passed! Step marked as complete.

Exiting. Run 'smc-upgrader --ai:continue' to continue with the next step.
```

### Validation Failure Example

If the validation command fails, the step remains incomplete:

```bash
$ smc-upgrader --ai:continue
Reading plan from: smc-upgrader-plan.md

Next step: Fix compilation failures
  Task: Resolve all compilation issues
  Tool: claude
  Validation command: mvn clean install -DskipAllTests
  Status: incomplete

What would you like to do?
  [E] Execute this step
  [C] Check if this step is complete
  [M] Mark this step as complete
  [X] Exit

Your choice: C

Running validation command: mvn clean install -DskipAllTests
Validation command exited with code: 1

Validation failed. Step remains incomplete.
Review the output above and run 'smc-upgrader --ai:continue' to try again.
```

### Manual Completion

Customers can also mark steps complete manually without running validation:

```bash
$ smc-upgrader --ai:continue
Reading plan from: smc-upgrader-plan.md

Next step: Resolve 8.6.x merge conflicts
  Task: Resolve remaining Git merge conflicts
  Tool: claude
  Validation command: git diff --check
  Status: incomplete

What would you like to do?
  [E] Execute this step
  [C] Check if this step is complete
  [M] Mark this step as complete
  [X] Exit

Your choice: M

Step marked as complete.

Exiting. Run 'smc-upgrader --ai:continue' to continue with the next step.
```

## Step Completion Behavior

The tool handles step completion differently based on the tool type:

### smc-upgrader Tool Steps
- **Automatically marked complete** after successful execution
- These are deterministic operations (git merges) where the tool knows if they succeeded
- Customer can immediately proceed to the next step
- Example: "Git merge from 8.5.x to 8.6.x"

### claude Tool Steps
- **NOT automatically marked complete** after execution
- These involve AI assistance and require customer validation
- Customer must explicitly mark complete using one of these methods:
  1. **[C] Check validation**: Runs validation command; auto-marks complete if exit code is 0
  2. **[M] Mark complete**: Manually marks complete without validation
- Examples: "Resolve merge conflicts", "Fix compilation failures"

This distinction ensures that deterministic operations proceed efficiently while AI-assisted tasks receive proper human oversight.

## Architecture

### New Components

#### 1. Configuration File: `ai-assist-config.json`

Located in `src/main/resources/ai-assist-config.json`

```json
{
  "versions": ["8.3.x", "8.4.x", "8.5.x", "8.6.x", "8.7.x", "8.8.x"],
  "defaultSteps": [
    {
      "title": "Git merge from {FROM_VERSION} to {TO_VERSION}",
      "task": "Upgrade source from {FROM_VERSION} to {TO_VERSION}",
      "tool": "smc-upgrader",
      "status": "incomplete"
    },
    {
      "title": "Resolve {TO_VERSION} merge conflicts",
      "task": "Resolve remaining Git merge conflicts",
      "tool": "claude",
      "validationCommand": "git diff --check",
      "status": "incomplete",
      "prompt": "We are in the process of doing an upgrade of the Self-Managed Commerce code base from version {FROM_VERSION} to version {TO_VERSION}. Review the merge conflicts in the current folder and help to address them. \"ours\" represents the customer's custom code base, and \"theirs\" represents the new Self-Managed Commerce release code. Continue working until all Git merge conflicts are resolved. We do not need to run Maven commands to check if the code compiles in this step."
    },
    {
      "title": "Fix compilation failures",
      "task": "Resolve all compilation issues",
      "tool": "claude",
      "validationCommand": "mvn clean install -DskipAllTests",
      "status": "incomplete",
      "prompt": "We are in the process of doing an upgrade of the Self-Managed Commerce code base from version {FROM_VERSION} to version {TO_VERSION}. Run the validation command above and help to fix any compilation issues. The upgrade notes at https://documentation.elasticpath.com/commerce/docs/{TO_VERSION}/release-notes.html#upgrade-notes may be helpful."
    },
    {
      "title": "Fix static analysis, unit test, and integration test failures",
      "task": "Resolve all static analysis, unit test, and integration test failures",
      "tool": "claude",
      "validationCommand": "mvn clean install -DskipSlowTests",
      "status": "incomplete",
      "prompt": "We are in the process of doing an upgrade of the Self-Managed Commerce code base from version {FROM_VERSION} to version {TO_VERSION}. Run the validation command above and help to fix any static analysis or test failures. The upgrade notes at https://documentation.elasticpath.com/commerce/docs/{TO_VERSION}/release-notes.html#upgrade-notes may be helpful."
    },
    {
      "title": "Fix Local Database Reset Failures",
      "task": "Resolve database reset failures",
      "tool": "claude",
      "validationCommand": "mvn clean install -Dreset-db -f extensions/database",
      "status": "incomplete",
      "prompt": "..."
    },
    {
      "title": "Fix Integration Server startup issues",
      "task": "Fix Integration Server startup issues",
      "tool": "claude",
      "validationCommand": "mvn clean package cargo:run -f extensions/integration/ext-integration-webapp",
      "status": "incomplete",
      "prompt": "..."
    },
    {
      "title": "Fix Batch Server startup issues",
      "task": "Fix Batch Server startup issues",
      "tool": "claude",
      "validationCommand": "mvn clean package cargo:run -f extensions/batch/ext-batch-webapp",
      "status": "incomplete",
      "prompt": "..."
    },
    {
      "title": "Fix Search Server startup issues",
      "task": "Fix Search Server startup issues",
      "tool": "claude",
      "validationCommand": "mvn clean package cargo:run -f extensions/search/ext-search-webapp",
      "status": "incomplete",
      "prompt": "..."
    },
    {
      "title": "Fix Cortex startup issues",
      "task": "Fix Cortex startup issues",
      "tool": "claude",
      "validationCommand": "mvn clean package cargo:run -f extensions/cortex/ext-cortex-webapp",
      "status": "incomplete",
      "prompt": "..."
    },
    {
      "title": "Fix Commerce Manager startup issues",
      "task": "Fix Commerce Manager startup issues",
      "tool": "claude",
      "validationCommand": "mvn clean package cargo:run -f extensions/cm/ext-cm-modules/ext-cm-webapp",
      "status": "incomplete",
      "prompt": "..."
    },
    {
      "title": "Fix Cucumber and Selenium test failures",
      "task": "Resolve all Cucumber and Selenium test failures",
      "tool": "claude",
      "validationCommand": "mvn clean install -DskipTests=true -DskipITests=true",
      "status": "incomplete",
      "prompt": "..."
    }
  ]
}
```

**Note**: The complete configuration includes 11 steps per version upgrade:
1. Git merge (smc-upgrader tool)
2. Resolve merge conflicts (claude)
3. Fix compilation failures (claude)
4. Fix static analysis/unit/integration tests (claude)
5. Fix local database reset failures (claude)
6. Fix Integration Server startup issues (claude)
7. Fix Batch Server startup issues (claude)
8. Fix Search Server startup issues (claude)
9. Fix Cortex startup issues (claude)
10. Fix Commerce Manager startup issues (claude)
11. Fix Cucumber and Selenium test failures (claude)

These steps cover the complete Self-Managed Commerce upgrade workflow from code merge through all service validations. See `SAMPLE_ai-assist-config.json` for the complete configuration with all prompts.

#### 2. Java Classes

**`AiPlanStep.java`** - Model class for a single plan step
```java
public class AiPlanStep {
    private String title;
    private String task;
    private String tool; // "smc-upgrader" or "claude"
    private String validationCommand; // optional
    private String status; // "incomplete" or "complete"
    private String prompt; // for claude steps

    // getters, setters, constructors
}
```

**`UpgradePath.java`** - Model for upgrade path configuration
```java
public class UpgradePath {
    private List<String> versions;
    private List<AiPlanStep> defaultSteps;

    // Methods to calculate intermediate versions
    public List<String> getIntermediateVersions(String from, String to);
}
```

**`AiPlanGenerator.java`** - Generates the upgrade plan
```java
public class AiPlanGenerator {
    private final UpgradePath upgradePath;
    private final String currentVersion;
    private final String targetVersion;

    public void generatePlan(File outputFile);
    private List<String> calculateVersionSequence();
    private List<AiPlanStep> expandStepsForVersions(List<String> versions);
    private String substituteVariables(String template, String from, String to);
}
```

**`PlanDocument.java`** - Parser and writer for the markdown plan
```java
public class PlanDocument {
    private List<AiPlanStep> steps;

    public static PlanDocument parse(File planFile);
    public void write(File planFile);
    public AiPlanStep getFirstIncompleteStep();
    public void updateStepStatus(int stepIndex, String status);
}
```

**`ClaudeCodeInvoker.java`** - Invokes Claude Code CLI
```java
public class ClaudeCodeInvoker {
    public void invokeClaude(String prompt, File workingDirectory) throws IOException;
    private File createTempPromptFile(String prompt);
}
```

**`AiPlanExecutor.java`** - Main execution controller for AI mode
```java
public class AiPlanExecutor {
    private final PlanDocument planDocument;
    private final UpgradeController upgradeController;
    private final ClaudeCodeInvoker claudeInvoker;

    public void executePlan();
    private void showStepMenu(AiPlanStep step);
    private void executeStep(AiPlanStep step);
    private boolean checkStepCompletion(AiPlanStep step);
}
```

#### 3. Modified Classes

**`SMCUpgraderCLI.java`** - Add new subcommands

Add two new options:
```java
@CommandLine.Option(names = { "--ai:start" },
    description = "Start AI-assisted upgrade mode and generate upgrade plan")
private boolean aiStart;

@CommandLine.Option(names = { "--ai:continue" },
    description = "Continue AI-assisted upgrade from saved plan")
private boolean aiContinue;
```

Modify the `call()` method to handle these new options.

## Plan Document Format

The generated `smc-upgrader-plan.md` follows this structure:

```markdown
# SMC Upgrader - AI Assist Plan

Upgrade from: 8.5.x
Upgrade to: 8.7.x
Generated: 2025-11-24

## Git merge from 8.5.x to 8.6.x

Task: Upgrade source from 8.5.x to 8.6.x
Tool: smc-upgrader
Status: incomplete

## Resolve 8.6.x merge conflicts

Task: Resolve remaining Git merge conflicts
Tool: claude
Validation command: git diff --check
Status: incomplete

We are in the process of doing an upgrade of the Self-Managed Commerce code base from version 8.5.x to version 8.6.x. Review the merge conflicts in the current folder and help to address them. "ours" represents the customer's custom code base, and "theirs" represents the new Self-Managed Commerce release code. Continue working until all Git merge conflicts are resolved. We do not need to run Maven commands to check if the code compiles in this step.

## Fix compilation failures

Task: Resolve all compilation issues
Tool: claude
Validation command: mvn clean install -DskipAllTests
Status: incomplete

We are in the process of doing an upgrade of the Self-Managed Commerce code base from version 8.5.x to version 8.6.x. Run the validation command above and help to fix any compilation issues. The upgrade notes at https://documentation.elasticpath.com/commerce/docs/8.6.x/release-notes.html#upgrade-notes may be helpful.

[... more steps ...]
```

### Parsing Rules

- Each `## ` (H2) header starts a new step
- Metadata fields are parsed as `Key: Value` pairs
- The prompt for Claude steps is any text after the metadata and before the next step
- Status field can be edited by customers or updated by the tool

## Implementation Phases

### Phase 1: Foundation
1. Create `UpgradePath` and `AiPlanStep` model classes
2. Create `ai-assist-config.json` configuration file
3. Implement `AiPlanGenerator` to generate plan documents

### Phase 2: Plan Management
4. Implement `PlanDocument` parser and writer
5. Add `--ai:start` command to CLI
6. Test plan generation for various version ranges

### Phase 3: Execution
7. Implement `ClaudeCodeInvoker`
8. Implement `AiPlanExecutor` with interactive menu
9. Add `--ai:continue` command to CLI
10. Integration testing

### Phase 4: Polish
11. Add error handling and user feedback
12. Add logging for AI assist operations
13. Update documentation
14. Add unit tests for new components

## Error Handling

- **Invalid target version**: Display error if version not in ai-assist-config.json
- **No upgrade path**: Display error if current version is newer than target
- **Existing plan file**: When running `--ai:start`, prompt for confirmation if plan exists; exit if user declines
- **Missing plan file**: When running `--ai:continue`, check if plan exists
- **Corrupted plan file**: Validate plan structure before execution
- **Claude not installed**: Detect if `claude` command is available
- **Validation command failures**: Report exit code but don't block progress

## Testing Strategy

### Unit Tests
- Test version sequence calculation
- Test markdown parsing and writing
- Test variable substitution in prompts
- Test step status updates

### Integration Tests
- Test full plan generation
- Test plan execution (mock Claude invocation)
- Test multi-version upgrade paths

### Manual Testing
- Test with real SMC codebase upgrade scenarios
- Verify Claude Code integration
- Test plan customization workflows

## Future Enhancements

1. **Resume capability**: Allow resuming from interrupted upgrades
2. **Progress tracking**: Show overall progress (e.g., "Step 5 of 12")
3. **Rollback support**: Ability to revert to previous step
4. **Custom step templates**: Allow customers to define reusable step templates
5. **Validation automation**: Optional auto-run of validation commands
6. **Plan branching**: Support alternative paths based on customization complexity
7. **AI advisor**: Pre-analyze codebase and suggest additional steps
8. **Integration with CI/CD**: Generate pipeline configurations

## Dependencies

### New Maven Dependencies
```xml
<!-- For JSON parsing of ai-assist-config.json -->
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.10.1</version>
</dependency>
```

### External Dependencies
- Claude Code CLI (must be installed by customer)
- Claude Pro account (required for Claude Code)

## Configuration Management

The `ai-assist-config.json` file should be:
- Packaged in the JAR resources
- Versioned with the tool
- Updated when new SMC versions are released
- Validated during build to ensure proper JSON structure

## Documentation Updates

1. Update README.md with AI assist mode instructions
2. Add examples of generated plans
3. Document plan customization options
4. Add troubleshooting section for Claude Code integration
5. Create video tutorial for AI-assisted upgrades

## Success Metrics

- Reduction in time to complete upgrades
- Reduction in manual conflict resolution errors
- Customer satisfaction scores
- Adoption rate of AI assist mode vs traditional mode
