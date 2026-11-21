# OSRS Clan Finder — Verified RuneLite Plugin

**OSRS Clan Finder** is a fully functional RuneLite plugin written 100% in English that enables Old School RuneScape players to discover verified clans, inspect real published recruitment requirements with traceable source URLs, and check their account's eligibility against each clan's requirements.

---

## Key Features

### 1. Account Type Classification
Every clan entry is prominently classified on both its directory card and its details page under one of four strict categories:
- **Ironman Only**
- **Main Accounts Only**
- **Mixed (Ironman and Main Accounts)**
- **Unconfirmed**

Clans are never assumed to accept Ironman or Main accounts without explicit verification from official sources.

### 2. Real and Verified Recruitment Requirements
Every clan entry displays only its actual published recruitment requirements from official clan websites, verified Wise Old Man group profiles, TempleOSRS verified group records, and official recruitment announcements. Supported requirement categories include:
- Minimum / Maximum Combat Level
- Minimum Total Level
- Minimum / Maximum Hitpoints Level (including 10 HP Skiller restrictions)
- Minimum Strength, Prayer, Ranged, Magic, Defence (and Maximum Defence for Pures), and Slayer Levels
- Minimum Experience Requirements
- Required Boss Kills & Required Raid Completions (Chambers of Xeric, Theatre of Blood, Tombs of Amascut)
- Required PvM Experience (EHB)
- Required Achievements & Additional Application Requirements
- Ironman or Main Account Restrictions

### 3. Requirement Verification System
Each individual requirement includes:
- **Exact Requirement Text**
- **Traceable Source URL** (with one-click **Open** and **Copy** actions)
- **Verification Status Badge**:
  - `Officially Verified`
  - `Community Confirmed`
  - `Unverified`
  - `Outdated`
- **Last Verification Date**

When requirements are not publicly listed, the plugin explicitly displays:
> `"Recruitment requirements not publicly available."`

When requirements are ambiguous or evaluated privately per application cycle, the plugin displays:
> `"Contact the clan leadership for confirmation."`

### 4. Structured Clan Database & Remote Updates
- Bundled verified database: `src/main/resources/com/osrsclanfinder/verified_clans.json`
- **Administrator Remote Updates (No Plugin Rebuild Required)**: Administrators can host an updated JSON file conforming to the `verified_clans.json` schema and configure its URL under **Remote Verified Clan JSON URL** in the plugin settings.
- **Integrity Safeguard**: Remote sync and periodic Wise Old Man API verification (`ClanRepositoryService`) never silently overwrite `Officially Verified` requirements with unverified or guessed values.

### 5. Interactive Player Eligibility Checker (`Check My Eligibility`)
Clicking **Check My Eligibility** on any clan card or clan details page compares your available OSRS statistics (captured live from your logged-in OSRS client or fetched via RSN lookup from Wise Old Man / official Hiscores) against every published requirement of the clan.
For each requirement, it displays:
- **Requirement Value**
- **Player's Current Value**
- **Status Badge**:
  - `Requirement Met`
  - `Requirement Not Met`
  - `Not Applicable`
  - `Unable to Verify`
- **Remaining Progress** (or how much you exceed the requirement by)
- Explicit notice whenever manual confirmation with clan leadership is required, plus a mandatory disclaimer that meeting numerical requirements does not constitute official clan acceptance.

### 6. Combinable Clan Discovery Filters
Players can combine multiple filters simultaneously:
- **Account Type**: `Ironman Only`, `Main Accounts Only`, `Mixed Clans`
- **Region**: `International Clans`, `Brazilian Clans`
- **Specialization**: `PvM`, `Raids`, `Endgame PvM`, `Skilling`, `Social`
- **Verification**: `Officially Verified Only`

---

## Building and Running the Plugin

### Run Unit & Data Integrity Tests
```powershell
./gradlew test
```

### Build the Plugin JAR
```powershell
./gradlew build shadowJar
```

### Launch RuneLite in Developer Mode with OSRS Clan Finder
```powershell
./gradlew run
```
