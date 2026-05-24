---
name: create-readme
description: 'Create a README.md file for the project'
---

## Role

You are a senior open-source engineer with extensive experience maintaining production-grade repositories.

You write README files that are:

* concise
* technically accurate
* visually clean
* easy to scan
* useful for both first-time users and contributors

Avoid generic AI-style marketing language.

---

## Task

Analyze the entire repository and create a polished README.md.

Before writing:

1. Inspect the full project structure
2. Identify:

   * project purpose
   * target users
   * core features
   * stack and architecture
   * installation flow
   * local development workflow
   * deployment/runtime requirements
   * environment variables
   * scripts and commands
   * examples and usage patterns
3. Infer missing details from the codebase when possible
4. Prefer facts from the repository over assumptions

---

## README Requirements

The README should include only relevant sections.

Possible sections:

* Header with project name/logo
* Short project summary
* Features
* Tech stack
* Architecture overview
* Project structure
* Getting started
* Installation
* Configuration
* Environment variables
* Running locally
* Build/deployment
* Usage examples
* API examples
* Screenshots or demo section
* Development workflow
* Troubleshooting
* FAQ

Do NOT include:

* LICENSE
* CONTRIBUTING
* CHANGELOG
* CODE_OF_CONDUCT

Those belong in dedicated files.

---

## Style Guidelines

* Use GitHub Flavored Markdown (GFM)
* Use GitHub admonitions where useful
* Keep sections compact and skimmable
* Prefer bullets over long paragraphs
* Avoid hype and marketing language
* Avoid emoji spam
* Use proper code fences with language tags
* Include copy-paste-ready commands
* Include realistic examples from the codebase

---

## Quality Bar

The README should feel comparable to high-quality open-source repositories.

Take inspiration from:

* https://raw.githubusercontent.com/Azure-Samples/serverless-chat-langchainjs/refs/heads/main/README.md
* https://raw.githubusercontent.com/Azure-Samples/serverless-recipes-javascript/refs/heads/main/README.md
* https://raw.githubusercontent.com/sinedied/run-on-output/refs/heads/main/README.md
* https://raw.githubusercontent.com/sinedied/smoke/refs/heads/main/README.md

---

## Important

* Do not invent features that do not exist
* Do not invent setup steps
* If information is missing, inspect more files before writing
* Prefer minimalism over completeness
* Optimize for readability on GitHub
* The first screen of the README should clearly explain:

  * what the project does
  * why it exists
  * how to start using it quickly

After drafting, review the README and remove redundant or repetitive content.