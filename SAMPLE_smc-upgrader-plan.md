# SMC Upgrader - AI Assist Plan

Upgrade from: 8.5.x
Upgrade to: 8.7.x
Generated: 2025-11-24

---

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

## Fix static analysis, unit test, and integration test failures

Task: Resolve all static analysis, unit test, and integration test failures
Tool: claude
Validation command: mvn clean install -DskipSlowTests
Status: incomplete

We are in the process of doing an upgrade of the Self-Managed Commerce code base from version 8.5.x to version 8.6.x. Run the validation command above and help to fix any static analysis or test failures. The upgrade notes at https://documentation.elasticpath.com/commerce/docs/8.6.x/release-notes.html#upgrade-notes may be helpful.

## Fix Local Database Reset Failures

Task: Resolve database reset failures
Tool: claude
Validation command: mvn clean install -Dreset-db -f extensions/database
Status: incomplete

We are in the process of doing an upgrade of the Self-Managed Commerce code base from version 8.5.x to version 8.6.x. Run the validation command above to reset the local database and help to fix any failures. The upgrade notes at https://documentation.elasticpath.com/commerce/docs/8.6.x/release-notes.html#upgrade-notes may be helpful.

## Fix Integration Server startup issues

Task: Fix Integration Server startup issues
Tool: claude
Validation command: mvn clean package cargo:run -f extensions/integration/ext-integration-webapp
Status: incomplete

We are in the process of doing an upgrade of the Self-Managed Commerce code base from version 8.5.x to version 8.6.x. Run the validation command above to start the Integration Server and help to fix any startup issues. The upgrade notes at https://documentation.elasticpath.com/commerce/docs/8.6.x/release-notes.html#upgrade-notes may be helpful.

## Fix Batch Server startup issues

Task: Fix Batch Server startup issues
Tool: claude
Validation command: mvn clean package cargo:run -f extensions/batch/ext-batch-webapp
Status: incomplete

We are in the process of doing an upgrade of the Self-Managed Commerce code base from version 8.5.x to version 8.6.x. Run the validation command above to start the Batch Server and help to fix any startup issues. The upgrade notes at https://documentation.elasticpath.com/commerce/docs/8.6.x/release-notes.html#upgrade-notes may be helpful.

## Fix Search Server startup issues

Task: Fix Search Server startup issues
Tool: claude
Validation command: mvn clean package cargo:run -f extensions/search/ext-search-webapp
Status: incomplete

We are in the process of doing an upgrade of the Self-Managed Commerce code base from version 8.5.x to version 8.6.x. Run the validation command above to start the Search Server and help to fix any startup issues. The upgrade notes at https://documentation.elasticpath.com/commerce/docs/8.6.x/release-notes.html#upgrade-notes may be helpful.

## Fix Cortex startup issues

Task: Fix Cortex startup issues
Tool: claude
Validation command: mvn clean package cargo:run -f extensions/cortex/ext-cortex-webapp
Status: incomplete

We are in the process of doing an upgrade of the Self-Managed Commerce code base from version 8.5.x to version 8.6.x. Run the validation command above to start Cortex and help to fix any startup issues. The upgrade notes at https://documentation.elasticpath.com/commerce/docs/8.6.x/release-notes.html#upgrade-notes may be helpful.

## Fix Commerce Manager startup issues

Task: Fix Commerce Manager startup issues
Tool: claude
Validation command: mvn clean package cargo:run -f extensions/cm/ext-cm-modules/ext-cm-webapp
Status: incomplete

We are in the process of doing an upgrade of the Self-Managed Commerce code base from version 8.5.x to version 8.6.x. Run the validation command above to start Commerce Manager and help to fix any startup issues. The upgrade notes at https://documentation.elasticpath.com/commerce/docs/8.6.x/release-notes.html#upgrade-notes may be helpful.

## Fix Cucumber and Selenium test failures

Task: Resolve all Cucumber and Selenium test failures
Tool: claude
Validation command: mvn clean install -DskipTests=true -DskipITests=true
Status: incomplete

We are in the process of doing an upgrade of the Self-Managed Commerce code base from version 8.5.x to version 8.6.x. Run the validation command above and help to fix any Cucumber or Selenium test failures. The upgrade notes at https://documentation.elasticpath.com/commerce/docs/8.6.x/release-notes.html#upgrade-notes may be helpful.

## Git merge from 8.6.x to 8.7.x

Task: Upgrade source from 8.6.x to 8.7.x
Tool: smc-upgrader
Status: incomplete

## Resolve 8.7.x merge conflicts

Task: Resolve remaining Git merge conflicts
Tool: claude
Validation command: git diff --check
Status: incomplete

We are in the process of doing an upgrade of the Self-Managed Commerce code base from version 8.6.x to version 8.7.x. Review the merge conflicts in the current folder and help to address them. "ours" represents the customer's custom code base, and "theirs" represents the new Self-Managed Commerce release code. Continue working until all Git merge conflicts are resolved. We do not need to run Maven commands to check if the code compiles in this step.

## Fix compilation failures

Task: Resolve all compilation issues
Tool: claude
Validation command: mvn clean install -DskipAllTests
Status: incomplete

We are in the process of doing an upgrade of the Self-Managed Commerce code base from version 8.6.x to version 8.7.x. Run the validation command above and help to fix any compilation issues. The upgrade notes at https://documentation.elasticpath.com/commerce/docs/8.7.x/release-notes.html#upgrade-notes may be helpful.

## Fix static analysis, unit test, and integration test failures

Task: Resolve all static analysis, unit test, and integration test failures
Tool: claude
Validation command: mvn clean install -DskipSlowTests
Status: incomplete

We are in the process of doing an upgrade of the Self-Managed Commerce code base from version 8.6.x to version 8.7.x. Run the validation command above and help to fix any static analysis or test failures. The upgrade notes at https://documentation.elasticpath.com/commerce/docs/8.7.x/release-notes.html#upgrade-notes may be helpful.

## Fix Local Database Reset Failures

Task: Resolve database reset failures
Tool: claude
Validation command: mvn clean install -Dreset-db -f extensions/database
Status: incomplete

We are in the process of doing an upgrade of the Self-Managed Commerce code base from version 8.6.x to version 8.7.x. Run the validation command above to reset the local database and help to fix any failures. The upgrade notes at https://documentation.elasticpath.com/commerce/docs/8.7.x/release-notes.html#upgrade-notes may be helpful.

## Fix Integration Server startup issues

Task: Fix Integration Server startup issues
Tool: claude
Validation command: mvn clean package cargo:run -f extensions/integration/ext-integration-webapp
Status: incomplete

We are in the process of doing an upgrade of the Self-Managed Commerce code base from version 8.6.x to version 8.7.x. Run the validation command above to start the Integration Server and help to fix any startup issues. The upgrade notes at https://documentation.elasticpath.com/commerce/docs/8.7.x/release-notes.html#upgrade-notes may be helpful.

## Fix Batch Server startup issues

Task: Fix Batch Server startup issues
Tool: claude
Validation command: mvn clean package cargo:run -f extensions/batch/ext-batch-webapp
Status: incomplete

We are in the process of doing an upgrade of the Self-Managed Commerce code base from version 8.6.x to version 8.7.x. Run the validation command above to start the Batch Server and help to fix any startup issues. The upgrade notes at https://documentation.elasticpath.com/commerce/docs/8.7.x/release-notes.html#upgrade-notes may be helpful.

## Fix Search Server startup issues

Task: Fix Search Server startup issues
Tool: claude
Validation command: mvn clean package cargo:run -f extensions/search/ext-search-webapp
Status: incomplete

We are in the process of doing an upgrade of the Self-Managed Commerce code base from version 8.6.x to version 8.7.x. Run the validation command above to start the Search Server and help to fix any startup issues. The upgrade notes at https://documentation.elasticpath.com/commerce/docs/8.7.x/release-notes.html#upgrade-notes may be helpful.

## Fix Cortex startup issues

Task: Fix Cortex startup issues
Tool: claude
Validation command: mvn clean package cargo:run -f extensions/cortex/ext-cortex-webapp
Status: incomplete

We are in the process of doing an upgrade of the Self-Managed Commerce code base from version 8.6.x to version 8.7.x. Run the validation command above to start Cortex and help to fix any startup issues. The upgrade notes at https://documentation.elasticpath.com/commerce/docs/8.7.x/release-notes.html#upgrade-notes may be helpful.

## Fix Commerce Manager startup issues

Task: Fix Commerce Manager startup issues
Tool: claude
Validation command: mvn clean package cargo:run -f extensions/cm/ext-cm-modules/ext-cm-webapp
Status: incomplete

We are in the process of doing an upgrade of the Self-Managed Commerce code base from version 8.6.x to version 8.7.x. Run the validation command above to start Commerce Manager and help to fix any startup issues. The upgrade notes at https://documentation.elasticpath.com/commerce/docs/8.7.x/release-notes.html#upgrade-notes may be helpful.

## Fix Cucumber and Selenium test failures

Task: Resolve all Cucumber and Selenium test failures
Tool: claude
Validation command: mvn clean install -DskipTests=true -DskipITests=true
Status: incomplete

We are in the process of doing an upgrade of the Self-Managed Commerce code base from version 8.6.x to version 8.7.x. Run the validation command above and help to fix any Cucumber or Selenium test failures. The upgrade notes at https://documentation.elasticpath.com/commerce/docs/8.7.x/release-notes.html#upgrade-notes may be helpful.

---

## Notes

Customers can edit this plan to add custom steps or modify existing ones. Each step will be executed in sequence when running `smc-upgrader --ai:continue`.

To mark a step as complete, change `Status: incomplete` to `Status: complete`.
