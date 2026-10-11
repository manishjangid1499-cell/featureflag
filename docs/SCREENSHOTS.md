# Application Screenshots

[Project overview](../README.md) · [Engineering reference](ENGINEERING.md)

The following captures were supplied from the application on **11 October 2026**. They show the current UI and sample deployment state. Counts, rollout settings, and flag status are examples, not performance measurements. Screenshots are stored without altering application data or inventing screen content.

## Dashboard

Account identity and role, environment summaries, recent lifecycle activity, and navigation.

![Owner dashboard with environment counts and recent activity](images/dashboard.png)

## Feature flag management

Environment and state filtering, rollout percentages, and Preview / mutation actions. The displayed disabled PROD flag illustrates a configuration state; it does not indicate integration failure.

![Feature flags table with DEV and PROD flags](images/feature-flags.png)

## Create a flag

Environment, initial state, rollout, description, schedules with timezone context, and target subject IDs. This viewport shows the form fields; its submit controls are below the visible area.

![Flag creation dialog with schedule and targeting configuration](images/create-feature-flag.png)

## Member administration

The supplied names and email addresses are fictitious test data, as confirmed by the project author. The listing illustrates OWNER, ADMIN, and DEVELOPER roles, account state, administrative actions, and invitation semantics.

![Member listing with fictitious test account names and email addresses](images/member-management.png)

## Invite a member

Administrators invite a recipient with a role; the recipient chooses their own password through the invitation flow.

![Invitation dialog with name, email, and role inputs](images/invite-member.png)

## Create an SDK credential

The dialog binds a server-side credential to one environment and explains recovery from creation failures. This capture is taken before generation and contains no raw SDK key.

![SDK key creation dialog scoped to DEV](images/create-sdk-key.png)

## Sign in

The management console requires an existing account; no public self-registration or shared administrative credentials are provided.

![Login screen with empty credentials](images/login.png)

## Remaining refreshes

The SDK-key metadata listing, Analytics, Audit, and Notifications pages still need current captures. Previous images for these views remain in the repository but are not presented here as refreshed screenshots.

For new captures, wait for loading to finish, use consistent browser zoom, and cover personal email addresses. Do not capture raw SDK keys, tokens, passwords, invitation URLs, or browser developer tools containing credentials.
