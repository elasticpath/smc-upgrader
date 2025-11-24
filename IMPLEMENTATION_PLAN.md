# AI Assist Mode - Implementation Plan

## Files to Create

### 1. Configuration File
- **`src/main/resources/ai-assist-config.json`**
  - Purpose: Defines valid upgrade versions and default step templates
  - Format: JSON
  - Contains: version list, default steps with variable placeholders

### 2. Model Classes

- **`src/main/java/com/elasticpath/tools/smcupgrader/ai/AiPlanStep.java`**
  - Purpose: Data model for a single plan step
  - Properties: title, task, tool, validationCommand, status, prompt
  - Methods: getters, setters, validation

- **`src/main/java/com/elasticpath/tools/smcupgrader/ai/UpgradePath.java`**
  - Purpose: Model for upgrade path configuration
  - Properties: versions, defaultSteps
  - Methods:
    - `getIntermediateVersions(String from, String to)`
    - `validateVersionPath(String from, String to)`
    - Static factory method to load from JSON

### 3. Core Implementation Classes

- **`src/main/java/com/elasticpath/tools/smcupgrader/ai/AiPlanGenerator.java`**
  - Purpose: Generate upgrade plan markdown file
  - Dependencies: UpgradePath, UpgradeController
  - Key methods:
    - `generatePlan(String targetVersion, File outputFile)` - Generates plan, checks for existing file
    - `promptForOverwrite(File planFile)` - Prompts user if plan exists, returns true to proceed
    - `calculateVersionSequence(String from, String to)`
    - `expandStepsForVersions(List<String> versions)`
    - `substituteVariables(String template, String from, String to)`

- **`src/main/java/com/elasticpath/tools/smcupgrader/ai/PlanDocument.java`**
  - Purpose: Parse and manipulate the markdown plan file
  - Key methods:
    - `static PlanDocument parse(File planFile)`
    - `write(File planFile)`
    - `getFirstIncompleteStep()`
    - `updateStepStatus(int stepIndex, String status)`
    - `getStep(int index)`
    - `getAllSteps()`

- **`src/main/java/com/elasticpath/tools/smcupgrader/ai/ClaudeCodeInvoker.java`**
  - Purpose: Invoke Claude Code CLI with prompts
  - Key methods:
    - `invokeClaude(String prompt, File workingDirectory)`
    - `createTempPromptFile(String prompt)`
    - `isClaudeInstalled()`

- **`src/main/java/com/elasticpath/tools/smcupgrader/ai/AiPlanExecutor.java`**
  - Purpose: Interactive executor for the upgrade plan
  - Dependencies: PlanDocument, UpgradeController, ClaudeCodeInvoker
  - Behavior: Finds first incomplete step, shows menu, performs ONE action, then exits
  - Key methods:
    - `executePlan(File planFile)` - Main entry point; loads plan, shows menu, exits after action
    - `showStepMenu(AiPlanStep step, int stepIndex)` - Displays step info and menu options
    - `executeStep(AiPlanStep step, int stepIndex)` - Executes step; if tool=smc-upgrader, marks complete and exits; if tool=claude, exits without marking complete
    - `executeSmcUpgraderStep(AiPlanStep step, int stepIndex)` - Runs internal merge/upgrade logic, marks step complete
    - `executeClaudeStep(AiPlanStep step)` - Launches Claude Code CLI then exits (does NOT mark complete)
    - `checkStepCompletion(AiPlanStep step, int stepIndex)` - Runs validation command; if exit code is 0, marks step complete and exits; otherwise exits without marking complete
    - `markStepComplete(int stepIndex)` - Marks step complete in plan without validation, exits
    - `promptUserChoice()` - Gets user menu selection

### 4. Utility Classes

- **`src/main/java/com/elasticpath/tools/smcupgrader/ai/MarkdownParser.java`**
  - Purpose: Parse markdown into structured data
  - Key methods:
    - `parseSteps(String markdown)`
    - `parseMetadata(String section)`

- **`src/main/java/com/elasticpath/tools/smcupgrader/ai/MarkdownWriter.java`**
  - Purpose: Write structured data to markdown
  - Key methods:
    - `generateMarkdown(List<AiPlanStep> steps, String from, String to)`

## Files to Modify

### 1. CLI Entry Point
- **`src/main/java/com/elasticpath/tools/smcupgrader/SMCUpgraderCLI.java`**
  - Changes:
    - Add `@CommandLine.Option` for `--ai:start`
    - Add `@CommandLine.Option` for `--ai:continue`
    - Modify `call()` method to handle AI modes
    - Add validation for mutually exclusive options

### 2. Build Configuration
- **`pom.xml`**
  - Changes:
    - Add Gson dependency for JSON parsing
    - Ensure resources directory is included in build

## Test Files to Create

### Unit Tests

- **`src/test/java/com/elasticpath/tools/smcupgrader/ai/UpgradePathTest.java`**
  - Test version sequence calculation
  - Test invalid version ranges
  - Test configuration loading

- **`src/test/java/com/elasticpath/tools/smcupgrader/ai/AiPlanGeneratorTest.java`**
  - Test plan generation for single version upgrade
  - Test plan generation for multi-version upgrade
  - Test variable substitution
  - Test overwrite prompt when plan file exists
  - Test exit behavior when user declines overwrite

- **`src/test/java/com/elasticpath/tools/smcupgrader/ai/PlanDocumentTest.java`**
  - Test markdown parsing
  - Test markdown writing
  - Test status updates
  - Test finding incomplete steps

- **`src/test/java/com/elasticpath/tools/smcupgrader/ai/MarkdownParserTest.java`**
  - Test parsing various markdown formats
  - Test metadata extraction
  - Test error handling for malformed documents

- **`src/test/java/com/elasticpath/tools/smcupgrader/ai/ClaudeCodeInvokerTest.java`**
  - Test Claude detection
  - Test temp file creation
  - Test process invocation (mocked)

- **`src/test/java/com/elasticpath/tools/smcupgrader/ai/AiPlanExecutorTest.java`**
  - Test smc-upgrader step execution automatically marks step complete and exits
  - Test claude step execution does NOT mark step complete and exits
  - Test manual mark complete works for any step type
  - Test validation check with exit code 0 marks step complete and exits
  - Test validation check with non-zero exit code keeps step incomplete and exits
  - Test that only one action is performed per invocation
  - Test finding first incomplete step
  - Test all menu options work correctly

### Integration Tests

- **`src/test/java/com/elasticpath/tools/smcupgrader/ai/AiWorkflowIntegrationTest.java`**
  - Test full workflow: generate plan -> execute step -> validate -> mark complete -> next step
  - Test plan customization scenarios
  - Test execution model: verify no automatic completion or progression

### Test Resources

- **`src/test/resources/ai-assist-config-test.json`**
  - Test configuration with limited versions

- **`src/test/resources/sample-plan.md`**
  - Sample plan document for parsing tests

## Implementation Order

### Sprint 1: Foundation & Models (Week 1)
1. Create package structure (`ai` subpackage)
2. Create `AiPlanStep.java` model
3. Create `ai-assist-config.json` configuration
4. Create `UpgradePath.java` with JSON loading
5. Write unit tests for `UpgradePath`
6. Update `pom.xml` with Gson dependency

**Deliverable**: Configuration can be loaded and version paths can be calculated

### Sprint 2: Plan Generation (Week 2)
7. Create `MarkdownWriter.java`
8. Create `AiPlanGenerator.java`
9. Write unit tests for plan generation
10. Integrate with `SMCUpgraderCLI` for `--ai:start` option
11. Manual testing of plan generation

**Deliverable**: `smc-upgrader --ai:start <version>` generates valid plan

### Sprint 3: Plan Parsing & Execution Framework (Week 3)
12. Create `MarkdownParser.java`
13. Create `PlanDocument.java`
14. Write unit tests for parsing and document manipulation
15. Create `ClaudeCodeInvoker.java`
16. Write unit tests for Claude invocation

**Deliverable**: Plans can be parsed and modified programmatically

### Sprint 4: Interactive Executor (Week 4)
17. Create `AiPlanExecutor.java` with interactive menu
18. Implement step execution for smc-upgrader tool steps
19. Implement step execution for claude tool steps
20. Integrate with `SMCUpgraderCLI` for `--ai:continue` option
21. End-to-end integration testing

**Deliverable**: `smc-upgrader --ai:continue` works end-to-end

### Sprint 5: Polish & Documentation (Week 5)
22. Add comprehensive error handling
23. Improve user prompts and feedback
24. Add logging for all AI operations
25. Update README.md
26. Create user guide with examples
27. Performance testing
28. Bug fixes from testing

**Deliverable**: Feature complete and documented

## Technical Decisions

### JSON Library: Gson
- Lightweight and well-suited for simple JSON parsing
- No additional annotation processing required
- Compatible with Java 8

### Markdown Format
- Human-readable and editable
- Simple parsing with regex/line-based approach
- Standard format customers are familiar with

### Process Invocation
- Use `ProcessBuilder` for Claude Code invocation
- Inherit stdin/stdout/stderr for interactive experience
- Write prompts to temp files to avoid command-line length limits

### Interactive Menu
- Use `System.console()` for input
- Fallback to `Scanner` if console not available
- Single-character commands for efficiency

### Execution Model
- **One action per invocation**: Each run of `--ai:continue` performs exactly one action then exits
- **Tool-based completion behavior**:
  - **smc-upgrader steps**: Automatically marked complete after successful execution (deterministic operations)
  - **claude steps**: NOT automatically marked complete; require explicit customer validation
- **Manual validation for AI steps**: Customers must run `--ai:continue` again to validate or mark claude steps complete
- **Deliberate progression**: Tool never automatically moves to the next step
- **Rationale**: Deterministic operations (git merges) proceed efficiently while AI-assisted tasks receive proper human oversight

### Error Handling Strategy
- Fail fast on invalid configuration
- Graceful degradation for missing Claude installation
- User-friendly error messages with actionable guidance

## Dependencies Added

```xml
<!-- pom.xml additions -->
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.10.1</version>
</dependency>
```

## Backward Compatibility

- All existing CLI options continue to work unchanged
- AI assist mode is opt-in via new flags
- No breaking changes to existing APIs
- Plan file is optional and doesn't affect non-AI workflows

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Claude Code not installed | Detect early and provide installation instructions |
| Invalid upgrade path | Validate against configuration before generating plan |
| Accidentally overwriting existing plan | Prompt for confirmation if plan exists, default to No |
| Corrupted plan file | Validate structure on load, provide helpful error messages |
| Long-running validation commands | Keep validation manual, let users decide when to run |
| Plan customization breaks parsing | Robust parser that handles variations |
| Version configuration becomes outdated | Document update process, validate in CI |

## Success Criteria

- [ ] Can generate plan for single-version upgrade
- [ ] Can generate plan for multi-version upgrade
- [ ] Prompts for confirmation when overwriting existing plan
- [ ] Exits gracefully when user declines overwrite
- [ ] Can parse and modify plan files
- [ ] Can execute smc-upgrader tool steps
- [ ] Can launch Claude Code for claude tool steps
- [ ] Can update step status in plan
- [ ] Plan can be customized by users
- [ ] All unit tests pass
- [ ] Integration tests pass
- [ ] Documentation is complete
- [ ] Manual end-to-end testing successful

## Documentation Deliverables

1. Design document (completed: `AI_ASSIST_MODE_DESIGN.md`)
2. Implementation plan (this document)
3. Updated README.md with AI assist examples
4. User guide with screenshots/examples
5. Javadoc for all new classes
6. Release notes entry
