# One-Shot Schedule Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a backend one-shot schedule generation endpoint and wire the generation page to use real employees and shift types.

**Architecture:** Add request/response DTOs and an orchestration service that creates a period, creates work shifts for selected shift types/date range, then delegates to existing `AdminOptimizationService`. Frontend service gets a `generateSchedule` method; the page loads backend employees/shift types and submits all selections at once.

**Tech Stack:** Spring Boot 4, JUnit/Mockito, React 19, TypeScript, Vitest.

---

## Tasks

- [ ] Add backend DTOs and failing service test for one-shot orchestration.
- [ ] Implement backend service/controller endpoint.
- [ ] Add frontend backend shift type service/types and generate service method with tests.
- [ ] Wire `ScheduleGenerationPage` to load real employees/shift types and call generate.
- [ ] Run focused backend/frontend tests, builds, lint, and runtime smoke.

## Self-Review

- Scope is one feature slice: one-shot generation API plus frontend wiring.
- No backend API additions beyond the single generate endpoint.
- Existing optimize endpoint remains supported.
