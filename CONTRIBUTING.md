# Contributing

Thanks for contributing to OrionFFA-Core.

## Development guidelines

- Keep changes focused on the feature or bug being addressed.
- Keep gameplay state in its owning service.
- Route player teleports through `TeleportService`.
- Do not call Bukkit player or entity APIs from asynchronous storage or background callbacks.
- Keep commands, permissions, tab completion, configuration, messages, and GUI actions consistent when changing a feature.
- Prefer small, understandable changes over unrelated refactors.

## Before submitting a change

1. Run `./gradlew clean build`.
2. Test affected commands, GUIs, and gameplay flows on the target Paper version when practical.
3. Check that configuration and message changes work with a fresh installation and an existing configuration when relevant.
4. Make sure the change does not introduce unnecessary dependencies or break optional integrations.
5. Write a clear commit or pull request description explaining the change and how it was tested.

## Pull requests

Keep pull requests focused and reviewable. For larger architectural changes, describe the intended design and affected systems before implementation where practical.

Bug fixes should include the observed problem, the cause when known, and the verification performed.

Feature changes should document any new commands, permissions, configuration options, messages, or user-facing behavior.
