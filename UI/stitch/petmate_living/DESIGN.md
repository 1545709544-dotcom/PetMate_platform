---
name: PetMate Living
colors:
  surface: '#f4fafd'
  surface-dim: '#d4dbdd'
  surface-bright: '#f4fafd'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#eef5f7'
  surface-container: '#e8eff1'
  surface-container-high: '#e2e9ec'
  surface-container-highest: '#dde4e6'
  on-surface: '#161d1f'
  on-surface-variant: '#584238'
  inverse-surface: '#2b3234'
  inverse-on-surface: '#ebf2f4'
  outline: '#8c7166'
  outline-variant: '#dfc0b3'
  surface-tint: '#a14000'
  primary: '#a14000'
  on-primary: '#ffffff'
  primary-container: '#ff7a32'
  on-primary-container: '#622400'
  inverse-primary: '#ffb694'
  secondary: '#0060ac'
  on-secondary: '#ffffff'
  secondary-container: '#68abff'
  on-secondary-container: '#003e73'
  tertiary: '#785900'
  on-tertiary: '#ffffff'
  tertiary-container: '#cb9900'
  on-tertiary-container: '#483400'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#ffdbcc'
  primary-fixed-dim: '#ffb694'
  on-primary-fixed: '#351000'
  on-primary-fixed-variant: '#7b2f00'
  secondary-fixed: '#d4e3ff'
  secondary-fixed-dim: '#a4c9ff'
  on-secondary-fixed: '#001c39'
  on-secondary-fixed-variant: '#004883'
  tertiary-fixed: '#ffdf9e'
  tertiary-fixed-dim: '#fabd00'
  on-tertiary-fixed: '#261a00'
  on-tertiary-fixed-variant: '#5b4300'
  background: '#f4fafd'
  on-background: '#161d1f'
  surface-variant: '#dde4e6'
  emergency-red: '#FF4D4F'
  success-green: '#52C41A'
  warning-amber: '#FAAD14'
  surface-soft: '#F8F9FA'
  sky-blue-light: '#EBF4FF'
typography:
  display-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 48px
    fontWeight: '800'
    lineHeight: 56px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
  headline-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  body-lg:
    fontFamily: Be Vietnam Pro
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: Be Vietnam Pro
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-md:
    fontFamily: Be Vietnam Pro
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-sm:
    fontFamily: Be Vietnam Pro
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 4px
  gutter-mobile: 16px
  gutter-desktop: 24px
  margin-mobile: 16px
  margin-desktop: 120px
  max-width-content: 1440px
  max-width-chat: 800px
---

## Brand & Style

The design system embodies a **Playful Professional** aesthetic, specifically tailored for the "PetMate" ecosystem. It balances the emotional warmth required for pet care with the technical reliability of AI-driven medical consultation. 

The style is a blend of **Modern Minimalism** and **Tactile Friendliness**. It utilizes high-energy colors, large organic shapes, and generous whitespace to create an environment that feels welcoming and easy to navigate for "fur baby" parents. The interface should evoke a sense of optimism and trust, using rounded geometry to soften the technical nature of the backend services.

Key visual pillars include:
- **Warmth:** Through a vibrant, sunny color palette.
- **Clarity:** Through structured card-based layouts and high-contrast typography.
- **Character:** Through the integration of cute, geometric pet-themed illustrations and iconography.

## Colors

The palette is anchored by "Energetic Orange" (#FF7A32), symbolizing the vitality and joy of pets. This is balanced by "Trust Blue" (#4A90E2) for functional elements and medical contexts, and "Sunshine Yellow" (#FFC107) for accents and rewards.

- **Primary (Orange):** Used for main CTAs, active brand moments, and primary pet profiles.
- **Secondary (Blue):** Dedicated to AI consultation, professional services, and interactive components like links and secondary buttons.
- **Tertiary (Yellow):** Reserved for "Golden" moments like achievements, SKU highlighting, and warning-level alerts.
- **Neutral:** A deep charcoal is used for text to ensure high legibility without the harshness of pure black, while the background remains a clean, soft white or light gray.

## Typography

This design system uses **Plus Jakarta Sans** for headlines to provide a modern, friendly, and slightly geometric feel that complements the rounded UI. **Be Vietnam Pro** is used for body text and labels due to its exceptional readability and contemporary warmth.

To ensure mobile compliance and prevent iOS auto-zoom, the minimum font size for input fields and primary body copy is locked at **16px**. For desktop, the scale expands to emphasize hierarchy in the 1920px design grid. Headlines use tight letter spacing and heavy weights to create a "bold" editorial feel, while body text uses a generous 1.5x line height to ensure clarity in long-form pet care articles.

## Layout & Spacing

The layout follows a **Fluid-Responsive Grid** system based on a 1920px desktop width, utilizing PostCSS for viewport-based scaling. 

- **Desktop (≥1024px):** A 12-column grid with wide margins. Content is capped at 1440px for standard pages, while specialized views like the AI Consultation are centered with a tighter 800px max-width to simulate an immersive chat experience.
- **Mobile (≤768px):** A single-column waterfall layout with 16px side margins. Interactive targets are strictly maintained at a minimum of 44x44px.
- **Spacing Rhythm:** Uses a 4px base unit. Component internal padding should follow a 4x increment (e.g., 8px, 12px, 16px, 24px) to maintain a consistent visual beat.

## Elevation & Depth

Visual hierarchy is achieved through **Tonal Layering** and **Soft Ambient Shadows**. 

1.  **Base Layer:** The surface-background uses `#FFFFFF` or `#F8F9FA`.
2.  **Card Layer:** Content modules (FeedCard, ProductCard) use white surfaces with a very subtle 1px border or a soft, diffused shadow (10% opacity of the brand primary color) to suggest they are floating just above the base.
3.  **Floating Action Layer:** Elements like the AI Consultation button use a higher elevation with a more pronounced shadow to indicate they are the topmost interactive layer.
4.  **Overlays:** Modals and drawers use a 40% opacity neutral backdrop blur to maintain context while focusing user attention.

## Shapes

The shape language is defined by **Rounded Geometry**. 

- **Standard Cards:** Use a 0.5rem (8px) radius to feel modern and organized.
- **Buttons & Chips:** Use a larger 1rem or full pill-shape to invite interaction and feel "soft" to the touch.
- **Avatars:** Always circular or "squircle" to reinforce the friendly pet-themed aesthetic.
- **Borders:** Where outlines are used, they should be "Hairline" (0.5pt/1px) with high-DPI scaling to remain crisp but unobtrusive.

## Components

- **Buttons:** Primary buttons use the brand-orange with white text, featuring a slight bottom-heavy shadow for a "pressable" feel. Secondary buttons use the blue-tinted outline style.
- **Cards (The "PetCard"):** These are the core building blocks. They must include a clear header, image container with a 4:3 or 1:1 aspect ratio, and a distinct footer for actions.
- **Chips:** Small, pill-shaped badges used for pet categories (e.g., "Dog", "Supplies") using low-saturation versions of the brand colors.
- **Input Fields:** Minimum 16px text, 12px vertical padding, and a 2px colored border focus state using the Secondary (Blue) color.
- **AI Chat Bubbles:** Distinctive shapes—user bubbles are neutral/gray, while AI responses are branded blue with a small "Sparkle" icon to denote machine generation.
- **Bottom TabBar (Mobile):** A sticky container with 44px icon targets, using a subtle glassmorphism effect (backdrop-blur) to show the content scrolling beneath.