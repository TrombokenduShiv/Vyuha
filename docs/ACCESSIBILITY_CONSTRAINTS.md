> [!IMPORTANT]
> **AI AGENT DIRECTIVE**
> This document contains strict architectural rules for VYUHA 2.0. Any AI agent or coding assistant operating in this repository must abide by these constraints. Do not deviate, simplify, or hallucinate outside these boundaries. Log any new learnings, corrections, or workflow resolutions to the `.ai/` directory.

# Accessibility Constraints
**Owner: Alaukik Saxena (HCI / Safety Interaction Engineer)**
**Consumer: Sneha (Jetpack Compose implementation)**

This document defines the binding accessibility requirements for all Vyuha intervention screens. Sneha MUST implement these constraints. Violations are defects, not style preferences.

---

## 1. Core Principles

1. **Coercion victims may have impaired cognitive capacity.** Panic, fear, and time pressure reduce reading comprehension, fine motor control, and decision-making ability. Accessibility is not optional — it is part of the safety thesis.
2. **Accessibility services are NOT fraud signals.** A user running TalkBack, Switch Access, or magnification must NOT be penalized. Play Integrity's app-access-risk semantics exclude verified accessibility services (per TRD Table 11 Failure Mode E).
3. **Every intervention must be fully operable via touch, keyboard (BT), and screen reader.** No gesture-only interactions.

---

## 2. Touch Target Requirements

| Requirement | Specification | Android Ref |
|---|---|---|
| Minimum touch target size | **48dp × 48dp** | Material Design guidelines |
| Minimum spacing between targets | **8dp** | Prevent accidental taps under stress |
| Primary action button | **Minimum 56dp height, full-width** | Emphasized primary action |
| Cancel button | **Minimum 48dp height, full-width** | Must be equally reachable |
| Button within bottom sheet (A1) | **56dp height, 16dp horizontal padding** | Bottom sheet interaction target |

> [!WARNING]
> Under coercion, users have elevated tremor and reduced fine motor precision. Minimum targets are MINIMUMS, not goals. Prefer larger targets wherever layout permits.

---

## 3. Text & Typography

| Requirement | Specification |
|---|---|
| Minimum body text size | **16sp** (user-scalable) |
| Minimum heading text size | **20sp** (user-scalable) |
| Minimum button text size | **16sp** |
| Font weight for headings | **Bold (700)** |
| Font weight for body | **Regular (400)** or **Medium (500)** |
| Maximum line length | **45 characters** per line |
| Line height | **1.5× font size** minimum |
| Text MUST respect `fontScale` | Yes — all sizes in `sp`, never `dp` |
| Language level | **Flesch-Kincaid Grade ≤ 5** (5th-grade reading level) |

---

## 4. Color & Contrast

| Requirement | Specification | WCAG Level |
|---|---|---|
| Normal text contrast ratio | **≥ 4.5:1** against background | AA |
| Large text contrast ratio (≥18sp bold or ≥24sp) | **≥ 3:1** against background | AA |
| Button label contrast | **≥ 4.5:1** against button background | AA |
| Icon contrast against background | **≥ 3:1** | AA |
| Color MUST NOT be the only indicator | Risk must be communicated via text + icon + color | AA |

### Severity Tier Color Verification

| Tier | Background | Text Color | Contrast Ratio | Pass? |
|---|---|---|---|---|
| TIER_1 | `#FFFFFF` | `#1E88E5` | 3.5:1 ⚠️ | Use `#1565C0` instead → **5.1:1** ✅ |
| TIER_2 | `#FFF3E0` | `#E65100` | 4.8:1 | ✅ |
| TIER_3 | `#FBE9E7` | `#BF360C` | 5.2:1 | ✅ |
| TIER_4 | `#D32F2F` | `#FFFFFF` | 5.5:1 | ✅ |
| TIER_5 | `#B71C1C` | `#FFFFFF` | 7.1:1 | ✅ |
| TIER_6 | `#9C27B0` | `#FFFFFF` | 4.6:1 | ✅ |

> [!IMPORTANT]
> **TIER_1 correction:** The original SEVERITY_MAPPING.md specifies `#1E88E5` blue text on white. This FAILS WCAG AA at 3.5:1. Sneha MUST use `#1565C0` (darker blue) for body text, keeping `#1E88E5` for icons/decorative elements only.

---

## 5. Screen Reader (TalkBack) Requirements

### Content Description Strategy

Every interactive and informational element MUST have a `contentDescription` or `semantics { }` block.

| Element | Content Description Pattern |
|---|---|
| Intervention heading | Read heading text verbatim: "Quick Check" |
| Intervention body | Read body text verbatim |
| Primary action button | "{label}. Tap to {action}." Example: "No, this is my choice. Tap to continue." |
| Cancel button | "Cancel Payment. Tap to cancel this transaction." |
| Timer display (A3) | "{N} seconds remaining before you can continue" |
| Call status indicator (A4) | "Call in progress" / "Call ended" |
| Screen sharing indicator (A4) | "Screen sharing active" / "Screen sharing stopped" |
| Contact card (A5) | "{contact_name}, Primary Contact. Tap to send verification request." |
| Severity icon | "{tier_name} risk level" |

### Focus Order

Screen reader traversal order for each intervention screen:

**A1 (MICRO_PROMPT) — Bottom Sheet:**
1. Heading: "Quick Check"
2. Body: question text
3. Button: "No, this is my choice"
4. Button: "I'm not sure"
5. Button: "Yes, someone asked me to"

**A2 (REFLECTION_CHALLENGE):**
1. Heading: "Take a Moment"
2. Body: reflection text (includes amount and payee)
3. Option 1: "Sending to family or friends"
4. Option 2: "Paying a merchant or business"
5. Option 3: "Someone asked me to send this"
6. Option 4: "I'd rather not say"
7. Button: "Cancel Payment"

**A3 (COOLING_DELAY):**
1. Heading: "Pause Before You Pay"
2. Body: delay explanation
3. Timer: live-updating countdown (announce at 10s, 5s, 0s)
4. Safety message
5. Button: "Cancel Payment"
6. Button: "Continue After Timer" (announced as disabled until timer=0)

**A4 (ISOLATION_BREAK):**
1. Heading: "Active Call Detected"
2. Body: instruction text
3. Status: call/screen state indicators (live region)
4. Button: "I Have Ended the Call"
5. Button: "Cancel Payment"

**A5 (TRUSTED_VERIFY):**
1. Heading: "Verify with Someone You Trust"
2. Body: explanation
3. Contact list: Primary, Secondary, Bank
4. Button: "Send Verification Request"
5. Button: "Cancel Payment"
6. Status: waiting indicator (live region)

**A6 (STEP_UP_REQUIRED):**
1. Heading: "Additional Verification Required"
2. Body: instruction
3. Button: "Verify Now"
4. Button: "Cancel Payment"

### Live Region Announcements

Elements that change dynamically MUST use `liveRegion = LiveRegionMode.Polite` (or `Assertive` for urgent updates):

| Element | Live Region Mode | Announce When |
|---|---|---|
| Timer countdown (A3) | Polite | At 10s, 5s, 0s (not every second) |
| Call status change (A4) | Assertive | When comm_active changes |
| Contact response (A5) | Assertive | When contact responds |
| Verification failure (A4) | Assertive | When claim contradicts telemetry |

---

## 6. Motion & Animation

| Requirement | Specification |
|---|---|
| Respect `prefers-reduced-motion` | Check `Settings.Global.ANIMATOR_DURATION_SCALE`; if 0, skip all animations |
| Maximum animation duration | **300ms** for transitions, **150ms** for micro-interactions |
| No autoplay video or infinite animation | — |
| Haptic feedback | Use `HapticFeedbackConstants` only; respect system vibration settings |
| Pulse animation (A4) | **3 cycles maximum**, then static. Do not loop indefinitely. |
| Countdown animation (A3) | Smooth decrement, not flickering. Must be readable at all times. |

---

## 7. Layout & Responsiveness

| Requirement | Specification |
|---|---|
| Minimum supported screen width | **320dp** (smallest Android phones) |
| Text wrapping | All text MUST wrap; never truncate with ellipsis on intervention text |
| Orientation | Support portrait only for intervention screens (per payment flow convention) |
| System font scaling | Test at **1.0×**, **1.3×**, **1.5×**, **2.0×** |
| Keyboard navigation | All buttons reachable via Tab/Enter with Bluetooth keyboard |
| Scrollable content | If content exceeds viewport at 2.0× font scale, screen MUST scroll |

---

## 8. Edge Cases

| Scenario | Required Behavior |
|---|---|
| User has TalkBack enabled | All interactions work; no friction penalty; do not flag as device risk |
| User has Switch Access enabled | All buttons reachable; correct traversal order |
| User has magnification enabled | Touch targets remain functional under magnification |
| User has color inversion | All elements remain distinguishable via shape/text; no color-only cues |
| User has large font (200%) | All text remains readable; screen scrolls if necessary |
| User is on a very small screen (320dp) | Layout does not break; buttons remain tappable; text wraps |
| User has disabled animations | No motion; transitions are instant; haptic may still fire |

---

## 9. Accessibility Testing Checklist (For Sneha)

Before marking any intervention screen as complete:

- [ ] All touch targets ≥ 48dp × 48dp
- [ ] All text in `sp` units (not `dp`)
- [ ] Contrast ratios pass WCAG AA (tool: Android Accessibility Scanner)
- [ ] TalkBack reads all elements in correct order
- [ ] TalkBack announces dynamic state changes (live regions)
- [ ] Screen functional at 2.0× font scale
- [ ] Screen functional with animations disabled
- [ ] No color-only risk indicators
- [ ] Cancel button reachable from every state
- [ ] Timer (A3) announces milestones (10s, 5s, 0s)
- [ ] Keyboard navigation works for all buttons

---

## 10. Non-Requirements (Explicitly Out of Scope for Hackathon)

| Item | Status | Production Plan |
|---|---|---|
| Multi-language support | Deferred | Internationalize via Android `strings.xml` |
| Right-to-left (RTL) layout | Deferred | Use Compose RTL-aware layout modifiers |
| Custom accessibility service testing | Deferred | Requires specialized tooling |
| Full WCAG AAA compliance | Deferred | AA is sufficient for hackathon |
| Voice control integration | Deferred | Requires Android Voice Access testing |
