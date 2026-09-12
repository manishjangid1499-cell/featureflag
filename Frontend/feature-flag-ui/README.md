# Feature Flag Platform UI

React, TypeScript, and Vite application for managing flags, members, invitations,
audit history, analytics, and notifications. Authentication and role checks are
enforced by the backend; browser guards control navigation and available actions.

Run from this directory with Node 24:

```sh
npm ci
npm run dev
```

Vite serves the UI on `http://localhost:5173` and proxies API requests to Gateway
on port 8080. Start the backend with its `local` profiles and configure invitation
links for the same frontend origin. See the [root setup guide](../../README.md)
and [local Kafka guide](../../docs/local-event-pipeline.md).

```sh
npm test
npm run lint
npm run build
```

Tests use Node's test runner and cover session expiry, role policies, invitation
delivery feedback, profile display, error decoding, and pagination. The build
checks TypeScript, bundles the application, and validates its Content Security
Policy. No production credentials belong in browser configuration.

The supported Compose deployment serves the built UI through unprivileged Nginx,
which forwards API requests to Gateway on the same browser origin.
