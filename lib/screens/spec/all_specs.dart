import 'screen_spec.dart';

// ============================================================================
// BATCH (a): KEYPAD SPECS
// ============================================================================

final keypadIosBloomPhysicsSpec = ScreenSpec(
  id: 'keypad_ios_bloom_physics',
  title: 'iOS 26 Liquid Bloom Physics',
  subtitle: 'Cluster origin, radial spring damping & bloom expansion',
  sections: [
    SectionSpec(
      header: 'Radial Bloom Kinetics',
      footer:
          'Buttons originate in an organic cluster behind Key 5 and shoot outward radially into their final grid coordinates.',
      items: [
        ItemSpec.slider(
          title: 'Bloom Duration',
          subtitle: 'Overall time for radial bloom expansion and settling',
          key: 'pin_ios_bloom_duration',
          min: 200,
          max: 2000,
          unit: 'ms',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Cluster Origin Compaction',
          subtitle: 'How tightly buttons start clustered around central Key 5',
          key: 'pin_ios_bloom_cluster_factor',
          min: 0,
          max: 1,
          unit: '%',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Spring Damping Ratio (ζ)',
          subtitle: 'Harmonic oscillation damping (lower = bouncier, higher = stiffer)',
          key: 'pin_ios_bloom_damping',
          min: 0.1,
          max: 1.0,
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Spring Angular Frequency (ω)',
          subtitle: 'Radial bounce speed and elasticity frequency',
          key: 'pin_ios_bloom_stiffness',
          min: 5,
          max: 50,
          unit: 'rad/s',
          enabledKey: 'engine_keypad_enabled',
        ),
      ],
    ),
  ],
);

final keypadGlassTuningSpec = ScreenSpec(
  id: 'keypad_glass_tuning',
  title: 'Liquid Glass Tuning',
  sections: [
    SectionSpec(
      header: 'Liquid Glass 2.0 Button Optics',
      footer:
          'Liquid Glass 2.0 lenses refract and reflect background content dynamically with physically-based dispersion and highlights.',
      items: [
        ItemSpec.slider(
          title: 'Lens Inward Refraction',
          subtitle: 'Real-time background refraction displacement',
          key: 'pin_btn_refraction',
          min: 0,
          max: 120,
          unit: 'dp',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Glass Bevel Thickness',
          subtitle: 'Width of the circular glass bevel curve',
          key: 'pin_btn_bevel',
          min: 0,
          max: 60,
          unit: 'dp',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Chromatic Dispersion',
          subtitle: 'Physical R/G/B spectral separation on button bevels',
          key: 'pin_btn_dispersion',
          min: 0,
          max: 0.5,
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Specular Highlight Rim',
          subtitle: 'Directional specular reflection intensity',
          key: 'pin_btn_specular',
          min: 0,
          max: 2,
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Inner Shadow Depth',
          subtitle: 'Backside shadow depth on button rim',
          key: 'pin_btn_inner_shadow',
          min: 0,
          max: 1,
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Vibrancy Saturation Boost',
          subtitle: 'Non-linear color boost inside glass lens',
          key: 'pin_btn_saturation',
          min: 50,
          max: 250,
          unit: '%',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Frosted Glass Blur',
          subtitle: 'Optional blur layer (0 = crystal clear lens)',
          key: 'pin_btn_blur',
          min: 0,
          max: 50,
          unit: 'dp',
          enabledKey: 'engine_keypad_enabled',
        ),
      ],
    ),
  ],
);

final keypadButtonsLayoutSpec = ScreenSpec(
  id: 'keypad_buttons_layout',
  title: 'Buttons Layout',
  sections: [
    SectionSpec(
      header: 'Button Geometry & Touch Interaction',
      footer:
          'Customize individual button dimensions, touch feedback scaling, and numeric/subtext typography.',
      items: [
        ItemSpec.slider(
          title: 'Button Circle Diameter',
          subtitle: 'Outer diameter of numeric key circles',
          key: 'pin_circle_diameter',
          min: 60,
          max: 120,
          unit: 'dp',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Liquid Press Scale',
          subtitle: 'Target scale ratio during touch down',
          key: 'pin_press_liquid_scale',
          min: 0.9,
          max: 1.5,
          unit: 'x',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Digit Font Size',
          subtitle: 'Typeface size for numbers 0-9',
          key: 'pin_digit_text_size',
          min: 24,
          max: 56,
          unit: 'sp',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.toggle(
          title: 'Show Klondike Letters',
          subtitle: 'Display ABC, DEF subtitle letters below digits',
          key: 'pin_show_klondike',
          enabledKey: 'engine_keypad_enabled',
        ),
      ],
    ),
  ],
);

final keypadPadLayoutSpec = ScreenSpec(
  id: 'keypad_pad_layout',
  title: 'PIN Pad Layout',
  sections: [
    SectionSpec(
      header: 'PIN Pad Layout & Grid Spacing',
      footer:
          'Adjust the vertical position and spacing grid of the PIN pad lockscreen layout.',
      items: [
        ItemSpec.slider(
          title: 'PIN Pad Vertical Lift',
          subtitle: 'Lifts entire keypad upwards to position bottom row (0)',
          key: 'pin_pad_lift',
          min: 0,
          max: 150,
          unit: 'dp',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'PIN Dots Y-Offset',
          subtitle: 'Vertical position of password dots indicator',
          key: 'pin_row0_offset',
          min: -250,
          max: 50,
          unit: 'dp',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Row Spacing',
          subtitle: 'Uniform vertical distance between number rows',
          key: 'pin_row_spacing',
          min: 0,
          max: 80,
          unit: 'dp',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Column Spacing',
          subtitle: 'Uniform horizontal distance between number columns',
          key: 'pin_col_spacing',
          min: 0,
          max: 40,
          unit: 'dp',
          enabledKey: 'engine_keypad_enabled',
        ),
      ],
    ),
  ],
);

final keypadStretchPhysicsSpec = ScreenSpec(
  id: 'keypad_stretch_physics',
  title: 'Pull & Stretch Elasticity',
  subtitle: 'Non-linear tension, thresholds & volume conservation',
  sections: [
    SectionSpec(
      header: 'Stretch Bounds & Resistance',
      footer:
          'Resistance increases non-linearly as distance increases, giving a physical rubberband liquid bubble feel.',
      items: [
        ItemSpec.slider(
          title: 'Max Stretch Threshold',
          subtitle: 'Maximum allowed drag displacement (fraction of diameter)',
          key: 'pin_drag_max_threshold',
          min: 0.05,
          max: 0.50,
          unit: '%',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Initial Pull Resistance',
          subtitle: 'Tactile stiffness when pulling starts (higher = firmer)',
          key: 'pin_drag_initial_stiffness',
          min: 0.05,
          max: 1.00,
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Non-Linear Resistance Power',
          subtitle: 'Rate at which resistance ramps up with pull distance',
          key: 'pin_drag_resistance_power',
          min: 0.5,
          max: 4.0,
          enabledKey: 'engine_keypad_enabled',
        ),
      ],
    ),
    SectionSpec(
      header: 'Liquid Volume & Tactile Optics',
      footer:
          'Volume preservation enforces s_x * s_y = baseScale^2 throughout deformation. When pulled along any angle, the transverse axis compresses proportionally.',
      items: [
        ItemSpec.slider(
          title: 'Anisotropic Stretch Factor',
          subtitle: 'Longitudinal elongation intensity along the drag vector',
          key: 'pin_drag_volume_stretch',
          min: 0.0,
          max: 0.5,
          unit: 'x',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Tactile Press Scale',
          subtitle: 'Baseline tactile button expansion on finger touch down',
          key: 'pin_press_liquid_scale',
          min: 0.9,
          max: 1.5,
          unit: 'x',
          enabledKey: 'engine_keypad_enabled',
        ),
      ],
    ),
  ],
);

final keypadReleasePhysicsSpec = ScreenSpec(
  id: 'keypad_release_physics',
  title: 'Release Snapback Dynamics',
  subtitle: 'Directional inertia mass transfer & rebound wobble',
  sections: [
    SectionSpec(
      header: 'Directional Inertia Squish',
      footer:
          'When released from an arbitrary angle and distance, the droplet snaps back along that vector. Fluid momentum slams forward at the opposite overshoot boundary.',
      items: [
        ItemSpec.slider(
          title: 'Inertia Squish Amplitude',
          subtitle: 'Liquid mass transfer squish scaled by pull distance & vector',
          key: 'pin_release_squish_amp',
          min: 0.0,
          max: 1.0,
          unit: 'x',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Snapback Duration',
          subtitle: 'Total time for center restitution and damped fluid wobble',
          key: 'pin_release_duration',
          min: 100,
          max: 1500,
          unit: 'ms',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Snapback Spring Tension',
          subtitle: 'Restoring spring tension crossing center into opposite overshoot',
          key: 'pin_release_overshoot',
          min: 0.5,
          max: 5.0,
          enabledKey: 'engine_keypad_enabled',
        ),
      ],
    ),
  ],
);

final keypadDelaysSpec = ScreenSpec(
  id: 'keypad_delays',
  title: 'Per-Key Delay Tuning',
  subtitle: 'Custom sequential entry timing offsets',
  sections: [
    SectionSpec(
      header: 'Numeric Keys (Row 1)',
      items: [
        ItemSpec.slider(
          title: 'Key 1 Delay',
          subtitle: 'Entrance delay for Key 1',
          key: 'pin_delay_key1',
          min: 0,
          max: 500,
          unit: 'ms',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Key 2 Delay',
          subtitle: 'Entrance delay for Key 2',
          key: 'pin_delay_key2',
          min: 0,
          max: 500,
          unit: 'ms',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Key 3 Delay',
          subtitle: 'Entrance delay for Key 3',
          key: 'pin_delay_key3',
          min: 0,
          max: 500,
          unit: 'ms',
          enabledKey: 'engine_keypad_enabled',
        ),
      ],
    ),
    SectionSpec(
      header: 'Numeric Keys (Row 2)',
      items: [
        ItemSpec.slider(
          title: 'Key 4 Delay',
          subtitle: 'Entrance delay for Key 4',
          key: 'pin_delay_key4',
          min: 0,
          max: 500,
          unit: 'ms',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Key 5 Delay',
          subtitle: 'Entrance delay for Key 5',
          key: 'pin_delay_key5',
          min: 0,
          max: 500,
          unit: 'ms',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Key 6 Delay',
          subtitle: 'Entrance delay for Key 6',
          key: 'pin_delay_key6',
          min: 0,
          max: 500,
          unit: 'ms',
          enabledKey: 'engine_keypad_enabled',
        ),
      ],
    ),
    SectionSpec(
      header: 'Numeric Keys (Row 3)',
      items: [
        ItemSpec.slider(
          title: 'Key 7 Delay',
          subtitle: 'Entrance delay for Key 7',
          key: 'pin_delay_key7',
          min: 0,
          max: 500,
          unit: 'ms',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Key 8 Delay',
          subtitle: 'Entrance delay for Key 8',
          key: 'pin_delay_key8',
          min: 0,
          max: 500,
          unit: 'ms',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Key 9 Delay',
          subtitle: 'Entrance delay for Key 9',
          key: 'pin_delay_key9',
          min: 0,
          max: 500,
          unit: 'ms',
          enabledKey: 'engine_keypad_enabled',
        ),
      ],
    ),
    SectionSpec(
      header: 'Bottom Row & Action Buttons',
      items: [
        ItemSpec.slider(
          title: 'Key 0 Delay',
          subtitle: 'Entrance delay for Key 0',
          key: 'pin_delay_key0',
          min: 0,
          max: 500,
          unit: 'ms',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Cancel / Delete Button Delay',
          subtitle: 'Entrance delay for Cancel / Delete action',
          key: 'pin_delay_cancel',
          min: 0,
          max: 500,
          unit: 'ms',
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.slider(
          title: 'Emergency Call Button Delay',
          subtitle: 'Entrance delay for Emergency Call button',
          key: 'pin_delay_emergency',
          min: 0,
          max: 500,
          unit: 'ms',
          enabledKey: 'engine_keypad_enabled',
        ),
      ],
    ),
  ],
);

final keypadPhysicsSpec = ScreenSpec(
  id: 'keypad_physics',
  title: 'PIN Pad Liquid Dynamics',
  subtitle: 'Inertial mass transfer & radial bloom kinetics',
  sections: [
    SectionSpec(
      header: 'Physics Subpages',
      footer: 'Tune the iOS 26 radial bloom entrance kinetics for PIN pad keys.',
      items: [
        ItemSpec.nav(
          title: 'iOS 26 Radial Bloom Physics',
          subtitle: 'Cluster origin, radial spring damping & bloom expansion',
          destinationSpec: keypadIosBloomPhysicsSpec,
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.nav(
          title: 'Pull & Stretch Elasticity',
          subtitle: 'Non-linear tension, thresholds & volume conservation',
          destinationSpec: keypadStretchPhysicsSpec,
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.nav(
          title: 'Release Snapback Dynamics',
          subtitle: 'Directional inertia mass transfer & rebound wobble',
          destinationSpec: keypadReleasePhysicsSpec,
          enabledKey: 'engine_keypad_enabled',
        ),
        ItemSpec.nav(
          title: 'Per-Key Delay Tuning',
          subtitle: 'Custom sequential entry timing offsets',
          destinationSpec: keypadDelaysSpec,
          enabledKey: 'engine_keypad_enabled',
        ),
      ],
    ),
  ],
);

// ============================================================================
// BATCH (b): SCREEN UNLOCK CASCADE & RADIAL SPECS
// ============================================================================

final screenUnlockCascadeGlobalsSpec = ScreenSpec(
  id: 'screen_unlock_cascade_globals',
  title: 'Global Kinetics & Velocity',
  sections: [
    SectionSpec(
      header: 'Kinetic Y-Axis Unlock Globals',
      footer:
          'Global animation duration multiplier, swipe velocity momentum coupling, and harmonic settling tension.',
      items: [
        ItemSpec.slider(
          title: 'Global Anim Duration',
          subtitle: 'Lower = faster overall animation, Higher = slower motion',
          key: 'unlock_duration_scale',
          min: 0.1,
          max: 3.0,
          unit: 'x',
          enabledKey: 'unlock_enabled',
        ),
        ItemSpec.slider(
          title: 'Velocity Impact',
          subtitle: 'Lower = subtle speed scaling, Higher = strong speed scaling',
          key: 'unlock_velocity_influence',
          min: 0.0,
          max: 5.0,
          enabledKey: 'unlock_enabled',
        ),
        ItemSpec.slider(
          title: 'Max Swipe Normalizer',
          subtitle: 'Ceiling velocity for maximum kinetic bounce',
          key: 'unlock_max_velocity',
          min: 1000,
          max: 25000,
          unit: 'px/s',
          enabledKey: 'unlock_enabled',
        ),
        ItemSpec.slider(
          title: 'Resting Damping',
          subtitle: 'Lower = bouncy settling curve, Higher = smooth critical damping',
          key: 'unlock_cascade_damping',
          min: 0.3,
          max: 1.5,
          enabledKey: 'unlock_enabled',
        ),
      ],
    ),
  ],
);

final screenUnlockCascadeDockSpec = ScreenSpec(
  id: 'screen_unlock_cascade_dock',
  title: 'Kinetic Dock Isolation',
  sections: [
    SectionSpec(
      header: 'Dock Isolation',
      footer:
          'Isolate the bottom dock icons from standard cascade rows with custom damping, response, and initial 3D Z-depth.',
      items: [
        ItemSpec.slider(
          title: 'Dock Damping',
          subtitle: 'Lower = bouncier dock icons, Higher = stiffer dock settling',
          key: 'unlock_kinetic_dock_damping',
          min: 0.1,
          max: 1.5,
          enabledKey: 'unlock_enabled',
        ),
        ItemSpec.slider(
          title: 'Dock Response',
          subtitle: 'Lower = snaps dock faster, Higher = slower dock arrival',
          key: 'unlock_kinetic_dock_response',
          min: 0.1,
          max: 1.5,
          enabledKey: 'unlock_enabled',
        ),
        ItemSpec.slider(
          title: 'Dock Z-Depth',
          subtitle: 'Starting 3D perspective depth for bottom dock',
          key: 'unlock_kinetic_dock_z_depth',
          min: 0.0,
          max: 2000.0,
          unit: 'dp',
          enabledKey: 'unlock_enabled',
        ),
      ],
    ),
  ],
);

final screenUnlockRadialKineticsSpec = ScreenSpec(
  id: 'screen_unlock_radial_kinetics',
  title: 'Radial Spring Kinetics',
  sections: [
    SectionSpec(
      header: 'Radial Dispersion Spring Physics',
      footer: 'Tune the outward harmonic spring dispersion of home screen icons from the radial center.',
      items: [
        ItemSpec.slider(
          title: 'Center Damping',
          subtitle: 'Lower = more bounce on release, Higher = stiffer settling',
          key: 'unlock_center_damping',
          min: 0.1,
          max: 1.5,
          enabledKey: 'unlock_enabled',
        ),
        ItemSpec.slider(
          title: 'Center Response',
          subtitle: 'Lower = snaps faster to position, Higher = slower float',
          key: 'unlock_center_response',
          min: 0.1,
          max: 1.0,
          enabledKey: 'unlock_enabled',
        ),
        ItemSpec.slider(
          title: 'Edge Damping Drop',
          subtitle: 'Lower = uniform bounce across screen, Higher = looser edges',
          key: 'unlock_edge_damping_drop',
          min: 0.0,
          max: 0.5,
          enabledKey: 'unlock_enabled',
        ),
        ItemSpec.slider(
          title: 'Edge Response Delay',
          subtitle: 'Lower = simultaneous expansion, Higher = edges lag behind',
          key: 'unlock_edge_response_delay',
          min: 0.0,
          max: 1.0,
          enabledKey: 'unlock_enabled',
        ),
        ItemSpec.slider(
          title: 'Z-Depth Scale',
          subtitle: 'Multiplies initial 3D depth perspective',
          key: 'unlock_z_depth_multiplier',
          min: 0.5,
          max: 4.0,
          unit: 'x',
          enabledKey: 'unlock_enabled',
        ),
      ],
    ),
    SectionSpec(
      header: 'Dynamic Clamp Bounds',
      footer: 'Define boundary constraints preventing harmonic physics from destabilizing or clipping.',
      items: [
        ItemSpec.slider(
          title: 'Minimum Response Clamp',
          subtitle: 'Floor boundary for radial dispersion response duration',
          key: 'unlock_min_response',
          min: 0.1,
          max: 1.0,
          enabledKey: 'unlock_enabled',
        ),
        ItemSpec.slider(
          title: 'Maximum Response Clamp',
          subtitle: 'Ceiling boundary for radial dispersion response duration',
          key: 'unlock_max_response',
          min: 0.2,
          max: 1.5,
          enabledKey: 'unlock_enabled',
        ),
        ItemSpec.slider(
          title: 'Minimum Damping Clamp',
          subtitle: 'Floor boundary for spring oscillation settling damping',
          key: 'unlock_min_damping',
          min: 0.2,
          max: 1.0,
          enabledKey: 'unlock_enabled',
        ),
        ItemSpec.slider(
          title: 'Maximum Damping Clamp',
          subtitle: 'Ceiling boundary for spring oscillation settling damping',
          key: 'unlock_max_damping',
          min: 0.3,
          max: 1.5,
          enabledKey: 'unlock_enabled',
        ),
      ],
    ),
  ],
);

final screenUnlockRadialDockSpec = ScreenSpec(
  id: 'screen_unlock_radial_dock',
  title: 'Radial Dock Settling',
  sections: [
    SectionSpec(
      header: 'Radial Dock Settling',
      footer: 'Independent harmonic damping and arrival timing for dock icons in Mode 0 radial unlock.',
      items: [
        ItemSpec.slider(
          title: 'Dock Damping',
          subtitle: 'Lower = bouncier dock icons, Higher = stiffer dock settling',
          key: 'unlock_dock_damping',
          min: 0.1,
          max: 1.5,
          enabledKey: 'unlock_enabled',
        ),
        ItemSpec.slider(
          title: 'Dock Response',
          subtitle: 'Lower = snaps dock faster, Higher = slower dock arrival',
          key: 'unlock_dock_response',
          min: 0.1,
          max: 1.5,
          enabledKey: 'unlock_enabled',
        ),
      ],
    ),
  ],
);

final screenUnlockCascadeZDepthSpec = ScreenSpec(
  id: 'screen_unlock_cascade_zdepth',
  title: 'Row 3D Perspective',
  sections: [
    SectionSpec(
      header: 'Row-By-Row 3D Perspective (Z-Depth)',
      footer: 'Customize the initial camera drop depth along the Z-axis for each horizontal icon row.',
      items: [
        for (int r = 0; r < 6; r++)
          ItemSpec.slider(
            title: 'Row $r Z-Depth',
            subtitle: 'Starting 3D perspective depth for row $r',
            key: 'unlock_r${r}_z',
            min: 0,
            max: 4000,
            unit: 'dp',
            enabledKey: 'unlock_enabled',
          ),
      ],
    ),
  ],
);

final screenUnlockCascadeBounceSpec = ScreenSpec(
  id: 'screen_unlock_cascade_bounce',
  title: 'Row Velocity Bounce',
  sections: [
    SectionSpec(
      header: 'Dynamic Velocity Bounce (Center vs Outer)',
      footer:
          'Fine-tune the elastic velocity bounce independently between inner columns and edge icons across each row.',
      items: [
        for (int r = 0; r < 6; r++) ...[
          ItemSpec.slider(
            title: 'Row $r Center Bounce',
            subtitle: 'Lower = subtle overshoot, Higher = pronounced center bounce',
            key: 'unlock_r${r}_center_bounce',
            min: 0.0,
            max: 1.0,
            enabledKey: 'unlock_enabled',
          ),
          ItemSpec.slider(
            title: 'Row $r Outer Bounce',
            subtitle: 'Lower = subtle overshoot, Higher = pronounced outer bounce',
            key: 'unlock_r${r}_outer_bounce',
            min: 0.0,
            max: 1.0,
            enabledKey: 'unlock_enabled',
          ),
        ],
      ],
    ),
  ],
);

final screenUnlockCascadeSettleSpec = ScreenSpec(
  id: 'screen_unlock_cascade_settle',
  title: 'Row Settling Dynamics',
  sections: [
    SectionSpec(
      header: 'Dynamic Settling Tension (Center vs Outer)',
      footer:
          'Calibrate independent settling arrival deceleration curves between inner and outer columns per row.',
      items: [
        for (int r = 0; r < 6; r++) ...[
          ItemSpec.slider(
            title: 'Row $r Center Settle',
            subtitle: 'Deceleration timing for center columns',
            key: 'unlock_r${r}_center_settle',
            min: 0.1,
            max: 2.0,
            enabledKey: 'unlock_enabled',
          ),
          ItemSpec.slider(
            title: 'Row $r Outer Settle',
            subtitle: 'Deceleration timing for outer columns',
            key: 'unlock_r${r}_outer_settle',
            min: 0.1,
            max: 2.0,
            enabledKey: 'unlock_enabled',
          ),
        ],
      ],
    ),
  ],
);

// ============================================================================
// BATCH (c): SHEETS SPECS
// ============================================================================

final lockScreenSheetGlassTuningSpec = ScreenSpec(
  id: 'lock_sheet_glass_tuning',
  title: 'Liquid Glass Tuning',
  sections: [
    SectionSpec(
      header: 'Optics & Refraction',
      footer: 'Refracts underlying wallpapers with crystal-clear 100% transparent interior.',
      items: [
        ItemSpec.slider(
          title: 'Side Edges Refraction',
          subtitle: 'Inward lens magnification along left and right borders',
          key: 'lock_card_side_refraction',
          min: 0,
          max: 120,
          unit: 'dp',
          enabledKey: 'lock_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Bottom Edge Refraction',
          subtitle: 'Upward lens refraction along bottom rim',
          key: 'lock_card_bottom_refraction',
          min: 0,
          max: 120,
          unit: 'dp',
          enabledKey: 'lock_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Lens Edge Stretch',
          subtitle: 'Outward boundary pixel distortion and dynamic squeeze expansion',
          key: 'lock_card_edge_stretch',
          min: 0,
          max: 60,
          unit: 'dp',
          enabledKey: 'lock_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Chromatic Dispersion',
          subtitle: 'Physical R/G/B spectral separation along surface normals',
          key: 'lock_card_dispersion',
          min: 0,
          max: 2.0,
          enabledKey: 'lock_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Caustic Rim Softness',
          subtitle: 'Normal-directed sample smoothing radius along glass bevels',
          key: 'lock_card_rim_softness',
          min: 0,
          max: 10,
          unit: 'dp',
          enabledKey: 'lock_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Vibrancy Saturation Boost',
          subtitle: 'Color vibrancy multiplication inside glass bevels',
          key: 'lock_card_saturation',
          min: 50,
          max: 250,
          unit: '%',
          enabledKey: 'lock_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Frosted Glass Blur',
          subtitle: 'Optional blur layer (0 = crystal clear true glass)',
          key: 'lock_card_blur_radius',
          min: 0,
          max: 50,
          unit: 'dp',
          enabledKey: 'lock_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Bottom Corners Radius',
          subtitle: 'Smooth curvature for bottom-left and bottom-right corners',
          key: 'lock_card_corner_radius',
          min: 0,
          max: 80,
          unit: 'dp',
          enabledKey: 'lock_sheet_enabled',
        ),
      ],
    ),
  ],
);

final lockScreenSheetLightingSpec = ScreenSpec(
  id: 'lock_sheet_lighting',
  title: 'Glass Lighting & Highlights',
  sections: [
    SectionSpec(
      header: 'Lighting & Highlights',
      footer: 'Simulates directional specular gleam and omnidirectional rim reflection across all glass edges.',
      items: [
        ItemSpec.slider(
          title: 'Specular Highlight Intensity',
          subtitle: 'Master surface lighting rim brightness',
          key: 'lock_card_specular',
          min: 0,
          max: 2.0,
          enabledKey: 'lock_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Perimeter Hairline Brightness',
          subtitle: 'Crisp Apple-style white edge boundary reflection along all 3 rims',
          key: 'lock_card_hairline',
          min: 0,
          max: 5.0,
          enabledKey: 'lock_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Inner Glow Band Intensity',
          subtitle: 'Soft refractive inner light diffusion inside bevel slopes',
          key: 'lock_card_inner_glow',
          min: 0,
          max: 1.0,
          enabledKey: 'lock_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Inner Shadow Depth',
          subtitle: 'Bevel inner groove shadow depth factor',
          key: 'lock_card_inner_shadow',
          min: 0,
          max: 1.0,
          enabledKey: 'lock_sheet_enabled',
        ),
      ],
    ),
  ],
);

final lockScreenSheetHomeIndicatorSpec = ScreenSpec(
  id: 'lock_sheet_home_indicator',
  title: 'Home Indicator Pill',
  sections: [
    SectionSpec(
      header: 'Home Indicator',
      items: [
        ItemSpec.toggle(
          title: 'Enable Home Indicator Pill',
          subtitle: 'Display interactive home bar pill at bottom edge',
          key: 'lock_pill_enabled',
          enabledKey: 'lock_sheet_enabled',
        ),
      ],
    ),
    SectionSpec(
      header: 'Pill Dimensions & Position',
      items: [
        ItemSpec.slider(
          title: 'Pill Width',
          subtitle: 'Horizontal size of the bottom home indicator',
          key: 'lock_pill_width',
          min: 50,
          max: 300,
          unit: 'dp',
          enabledKey: 'lock_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Pill Height',
          subtitle: 'Vertical thickness of the indicator bar',
          key: 'lock_pill_height',
          min: 2,
          max: 20,
          unit: 'dp',
          enabledKey: 'lock_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Bottom Margin',
          subtitle: 'Distance from screen bottom to indicator',
          key: 'lock_pill_bottom_margin',
          min: 0,
          max: 50,
          unit: 'dp',
          enabledKey: 'lock_sheet_enabled',
        ),
      ],
    ),
  ],
);

final notificationCenterSheetGlassTuningSpec = ScreenSpec(
  id: 'notif_sheet_glass_tuning',
  title: 'Liquid Glass Tuning',
  sections: [
    SectionSpec(
      header: 'Optics & Refraction',
      footer: 'Refracts underlying applications and wallpapers with crystal-clear 100% transparent interior.',
      items: [
        ItemSpec.slider(
          title: 'Side Edges Refraction',
          subtitle: 'Inward lens magnification along left and right borders',
          key: 'notif_card_side_refraction',
          min: 0,
          max: 120,
          unit: 'dp',
          enabledKey: 'notif_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Bottom Edge Refraction',
          subtitle: 'Upward lens refraction along bottom rim',
          key: 'notif_card_bottom_refraction',
          min: 0,
          max: 80,
          unit: 'dp',
          enabledKey: 'notif_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Chromatic Dispersion',
          subtitle: 'Physical R/G/B spectral separation along surface normals',
          key: 'notif_card_dispersion',
          min: 0,
          max: 1.0,
          enabledKey: 'notif_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Caustic Rim Softness',
          subtitle: 'Normal-directed 3-tap sample smoothing radius along glass bevels',
          key: 'notif_card_rim_softness',
          min: 0,
          max: 10,
          unit: 'dp',
          enabledKey: 'notif_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Saturation Boost Factor',
          subtitle: 'Color vibrancy multiplication inside the glass lens',
          key: 'notif_card_saturation',
          min: 50,
          max: 200,
          unit: '%',
          enabledKey: 'notif_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Lens Blur Radius',
          subtitle: 'Internal gaussian dispersion blur filter',
          key: 'notif_card_blur_radius',
          min: 0,
          max: 60,
          unit: 'dp',
          enabledKey: 'notif_sheet_enabled',
        ),
      ],
    ),
  ],
);

final notificationCenterSheetLightingSpec = ScreenSpec(
  id: 'notif_sheet_lighting',
  title: 'Glass Lighting & Highlights',
  sections: [
    SectionSpec(
      header: 'Lighting & Highlights',
      footer: 'Simulates directional specular gleam and omnidirectional rim reflection across all glass edges.',
      items: [
        ItemSpec.slider(
          title: 'Specular Highlight Intensity',
          subtitle: 'Master surface lighting rim brightness',
          key: 'notif_card_specular',
          min: 0,
          max: 2.0,
          enabledKey: 'notif_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Perimeter Hairline Brightness',
          subtitle: 'Crisp Apple-style white edge boundary reflection along all 3 rims',
          key: 'notif_card_hairline',
          min: 0,
          max: 5.0,
          enabledKey: 'notif_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Inner Glow Band Intensity',
          subtitle: 'Soft refractive inner light diffusion inside bevel slopes',
          key: 'notif_card_inner_glow',
          min: 0,
          max: 1.0,
          enabledKey: 'notif_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Inner Shadow Depth',
          subtitle: 'Bevel inner groove shadow depth factor',
          key: 'notif_card_inner_shadow',
          min: 0,
          max: 1.0,
          enabledKey: 'notif_sheet_enabled',
        ),
      ],
    ),
  ],
);

final notificationCenterSheetHomeIndicatorSpec = ScreenSpec(
  id: 'notif_sheet_home_indicator',
  title: 'Home Indicator Pill',
  sections: [
    SectionSpec(
      header: 'Pill Dimensions & Position',
      items: [
        ItemSpec.slider(
          title: 'Pill Width',
          subtitle: 'Horizontal size of the bottom home indicator',
          key: 'notif_pill_width',
          min: 50,
          max: 300,
          unit: 'dp',
          enabledKey: 'notif_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Pill Height',
          subtitle: 'Vertical thickness of the indicator bar',
          key: 'notif_pill_height',
          min: 2,
          max: 20,
          unit: 'dp',
          enabledKey: 'notif_sheet_enabled',
        ),
        ItemSpec.slider(
          title: 'Bottom Margin',
          subtitle: 'Distance from screen bottom to indicator',
          key: 'notif_pill_bottom_margin',
          min: 0,
          max: 50,
          unit: 'dp',
          enabledKey: 'notif_sheet_enabled',
        ),
      ],
    ),
  ],
);

// ============================================================================
// BATCH (d): OPTICS, APPEARANCE & TRANSITIONS SPECS
// ============================================================================

final morphPhysicsSpec = ScreenSpec(
  id: 'morph_physics',
  title: 'Morph Physics',
  subtitle: 'Dialog Morph Curve & Mass Engine',
  sections: [
    SectionSpec(
      header: 'Timing & Speed Multipliers',
      items: [
        ItemSpec.slider(
          title: 'Morph Speed Multiplier',
          subtitle: 'Lower = slow-motion inspection, Higher = instantaneous snap',
          key: 'morph_speed_multiplier',
          min: 0.1,
          max: 2.0,
          unit: 'x',
        ),
        ItemSpec.slider(
          title: 'Circle Collapse Ratio',
          subtitle: 'Lower = quick snap to circle, Higher = prolonged mass condensation',
          key: 'morph_circle_collapse_ratio',
          min: 0.15,
          max: 0.60,
          unit: '%',
        ),
        ItemSpec.slider(
          title: 'Spring Stiffness',
          subtitle: 'Lower = relaxed harmonic swing, Higher = high-tension snappy stroke',
          key: 'morph_spring_stiffness',
          min: 20,
          max: 600,
        ),
        ItemSpec.slider(
          title: 'Spring Damping Ratio',
          subtitle: 'Lower = elastic oscillation bounces, Higher = critical no-bounce damping',
          key: 'morph_spring_damping',
          min: 0.40,
          max: 1.00,
        ),
      ],
    ),
    SectionSpec(
      header: '3D Geometry & Trajectory',
      items: [
        ItemSpec.slider(
          title: 'Intermediate Circle Diameter',
          subtitle: 'Lower = compact core pill, Higher = larger intermediate droplet',
          key: 'morph_circle_diameter_dp',
          min: 20,
          max: 100,
          unit: 'dp',
        ),
        ItemSpec.slider(
          title: 'Z-Axis Plunge Depth',
          subtitle: 'Lower = flat 2D travel, Higher = deeper 3D depth into screen',
          key: 'morph_z_plunge_depth',
          min: 0.0,
          max: 1.0,
          unit: '%',
        ),
        ItemSpec.slider(
          title: 'Gravity Swoop Y',
          subtitle: 'Lower = straight diagonal flight, Higher = pronounced parabolic arc',
          key: 'morph_gravity_swoop_dp',
          min: 0,
          max: 150,
          unit: 'dp',
        ),
        ItemSpec.slider(
          title: 'Landing Y-Bounce',
          subtitle: 'Lower = soft landing, Higher = energetic vertical harmonic rebound',
          key: 'morph_y_bounce_amount_dp',
          min: 0,
          max: 50,
          unit: 'dp',
        ),
      ],
    ),
    SectionSpec(
      header: 'Material Elasticity & Content Bloom',
      items: [
        ItemSpec.slider(
          title: '4-Edge Isotropic Jelly Recoil',
          subtitle: 'Lower = rigid container edges, Higher = rubbery recoil overshoot',
          key: 'morph_jelly_overshoot',
          min: 0.0,
          max: 0.15,
          unit: '%',
        ),
        ItemSpec.slider(
          title: 'Content Outward Stretch',
          subtitle: 'Lower = static content appearance, Higher = dynamic center-bloom stretch',
          key: 'morph_content_stretch',
          min: 0.0,
          max: 0.15,
          unit: '%',
        ),
        ItemSpec.slider(
          title: 'Lens Refraction Surge',
          subtitle: 'Lower = uniform optics, Higher = intense magnification lens surge in-flight',
          key: 'morph_lens_surge_dp',
          min: 0,
          max: 60,
          unit: 'dp',
        ),
        ItemSpec.slider(
          title: 'Motion Blur Surge',
          subtitle: 'Lower = sharp backdrop, Higher = dynamic speed-blur distortion surge',
          key: 'morph_motion_blur_surge_dp',
          min: 0,
          max: 80,
          unit: 'dp',
        ),
      ],
    ),
  ],
);

final toggleAppearanceSpec = ScreenSpec(
  id: 'toggle_appearance',
  title: 'Toggles Appearance',
  sections: [
    SectionSpec(
      header: 'Accent Color',
      items: [
        ItemSpec.dropdown(
          title: 'Toggle Switch Accent',
          key: 'toggle_accent',
          options: const ['Green', 'Blue', 'Pink', 'Purple', 'Orange'],
          optionValues: const ['Green', 'Blue', 'Pink', 'Purple', 'Orange'],
        ),
      ],
    ),
    SectionSpec(
      header: 'Liquid Glass Optics',
      items: [
        ItemSpec.slider(
          title: 'Blur',
          subtitle: 'Backdrop gaussian blur radius',
          key: 'toggle_glass_blur',
          min: 0,
          max: 30,
          unit: 'dp',
        ),
        ItemSpec.slider(
          title: 'Lens Size',
          subtitle: 'Curvature radius of refraction lens',
          key: 'toggle_glass_lens_size',
          min: 0,
          max: 20,
          unit: 'dp',
        ),
        ItemSpec.slider(
          title: 'Lens Depth',
          subtitle: 'Optical refraction extrusion depth',
          key: 'toggle_glass_lens_depth',
          min: 0,
          max: 30,
          unit: 'dp',
        ),
        ItemSpec.slider(
          title: 'Highlight Strength',
          subtitle: 'Specular Fresnel rim highlight intensity',
          key: 'toggle_glass_highlight',
          min: 0,
          max: 1.0,
          unit: '%',
        ),
        ItemSpec.slider(
          title: 'Shadow Radius',
          subtitle: 'Diffusion radius of glass drop shadow',
          key: 'toggle_glass_shadow',
          min: 0,
          max: 20,
          unit: 'dp',
        ),
      ],
    ),
  ],
);

final togglePhysicsSpec = ScreenSpec(
  id: 'toggle_physics',
  title: 'Toggles Drag Physics',
  sections: [
    SectionSpec(
      header: 'Drag Physics',
      items: [
        ItemSpec.slider(
          title: 'Drag Sensitivity',
          subtitle: 'Touch tracking movement ratio',
          key: 'toggle_glass_drag_sensitivity',
          min: 0.1,
          max: 2.0,
        ),
        ItemSpec.slider(
          title: 'Spring Bounciness',
          subtitle: 'Lower = more overshoot bounce, Higher = stiffer vibration damping',
          key: 'toggle_glass_spring_damping',
          min: 0.2,
          max: 1.5,
        ),
        ItemSpec.slider(
          title: 'Spring Stiffness',
          subtitle: 'Lower = slower return oscillation, Higher = rapid snap settling',
          key: 'toggle_glass_spring_stiffness',
          min: 50,
          max: 1500,
        ),
      ],
    ),
  ],
);

final sliderAppearanceSpec = ScreenSpec(
  id: 'slider_appearance',
  title: 'Sliders Appearance',
  sections: [
    SectionSpec(
      header: 'Accent Color',
      items: [
        ItemSpec.dropdown(
          title: 'Slider Track Accent',
          key: 'slider_accent',
          options: const ['Blue', 'Green', 'Pink', 'Purple', 'Orange'],
          optionValues: const ['Blue', 'Green', 'Pink', 'Purple', 'Orange'],
        ),
      ],
    ),
    SectionSpec(
      header: 'Liquid Glass Optics',
      items: [
        ItemSpec.slider(
          title: 'Blur',
          subtitle: 'Backdrop gaussian blur radius',
          key: 'slider_glass_blur',
          min: 0,
          max: 30,
          unit: 'dp',
        ),
        ItemSpec.slider(
          title: 'Lens Size',
          subtitle: 'Curvature radius of refraction lens',
          key: 'slider_glass_lens_size',
          min: 0,
          max: 30,
          unit: 'dp',
        ),
        ItemSpec.slider(
          title: 'Lens Depth',
          subtitle: 'Optical refraction extrusion depth',
          key: 'slider_glass_lens_depth',
          min: 0,
          max: 40,
          unit: 'dp',
        ),
        ItemSpec.slider(
          title: 'Highlight Strength',
          subtitle: 'Specular Fresnel rim highlight intensity',
          key: 'slider_glass_highlight',
          min: 0,
          max: 1.0,
          unit: '%',
        ),
        ItemSpec.slider(
          title: 'Shadow Radius',
          subtitle: 'Diffusion radius of glass drop shadow',
          key: 'slider_glass_shadow',
          min: 0,
          max: 20,
          unit: 'dp',
        ),
      ],
    ),
  ],
);

final sliderPhysicsSpec = ScreenSpec(
  id: 'slider_physics',
  title: 'Sliders Drag Physics',
  sections: [
    SectionSpec(
      header: 'Drag Physics',
      items: [
        ItemSpec.slider(
          title: 'Drag Sensitivity',
          subtitle: 'Touch tracking movement ratio',
          key: 'slider_glass_drag_sensitivity',
          min: 0.1,
          max: 2.0,
        ),
        ItemSpec.slider(
          title: 'Spring Bounciness',
          subtitle: 'Lower = more overshoot bounce, Higher = stiffer vibration damping',
          key: 'slider_glass_spring_damping',
          min: 0.2,
          max: 1.5,
        ),
        ItemSpec.slider(
          title: 'Spring Stiffness',
          subtitle: 'Lower = slower return oscillation, Higher = rapid snap settling',
          key: 'slider_glass_spring_stiffness',
          min: 50,
          max: 2000,
        ),
      ],
    ),
  ],
);

final appTransitionsSpec = ScreenSpec(
  id: 'app_transitions',
  title: 'App Transitions',
  sections: [
    SectionSpec(
      items: [
        ItemSpec.toggle(
          title: 'Enable Enhanced Animation Engine',
          subtitle: 'Enhances app open and close animations (not recommended)',
          key: 'ios_anim_enabled',
        ),
      ],
    ),
    SectionSpec(
      header: 'Spring Dynamics & Speed',
      items: [
        ItemSpec.slider(
          title: 'Animation Speed',
          key: 'ios_anim_speed',
          min: 0.5,
          max: 2.5,
          unit: 'x',
          enabledKey: 'ios_anim_enabled',
        ),
        ItemSpec.slider(
          title: 'Opening Spring Damping',
          subtitle: 'Lower = more bounce, Higher = smoother settling',
          key: 'ios_anim_open_spring_damping',
          min: 0.3,
          max: 1.5,
          enabledKey: 'ios_anim_enabled',
        ),
        ItemSpec.slider(
          title: 'Closing Spring Damping',
          subtitle: 'Lower = more bounce, Higher = smoother settling',
          key: 'ios_anim_close_spring_damping',
          min: 0.3,
          max: 1.5,
          enabledKey: 'ios_anim_enabled',
        ),
        ItemSpec.toggle(
          title: 'Opening Uplift Surge',
          subtitle: 'Initial upward launch velocity surge on app open',
          key: 'ios_anim_open_uplift_surge',
          enabledKey: 'ios_anim_enabled',
        ),
        ItemSpec.slider(
          title: 'Opening Surge Lift',
          subtitle: 'Momentum elevation surge upon opening application',
          key: 'ios_anim_open_surge_lift',
          min: 0.5,
          max: 2.0,
          unit: 'x',
          enabledKey: 'ios_anim_enabled',
        ),
        ItemSpec.toggle(
          title: 'Closing Uplift Surge',
          subtitle: 'Parabolic arc upward trajectory surge on app close',
          key: 'ios_anim_close_uplift_surge',
          enabledKey: 'ios_anim_enabled',
        ),
        ItemSpec.slider(
          title: 'Closing Arc Apex Lift',
          subtitle: 'Parabolic arc trajectory height multiplier on app close',
          key: 'ios_anim_arc_apex_lift',
          min: 0.5,
          max: 2.0,
          unit: 'x',
          enabledKey: 'ios_anim_enabled',
        ),
        ItemSpec.toggle(
          title: 'Keystone Squeeze',
          subtitle: 'Perspective trapezoid squeeze distortion during icon expansion',
          key: 'ios_anim_keystone_squeeze',
          enabledKey: 'ios_anim_enabled',
        ),
      ],
    ),
    SectionSpec(
      header: 'Folder & Recents Gestures',
      items: [
        ItemSpec.toggle(
          title: '1:1 Folder Icon Trajectories',
          subtitle: 'Desktop preview icons track 1:1 into open folder positions',
          key: 'ios_anim_folder_tracking',
          enabledKey: 'ios_anim_enabled',
        ),
        ItemSpec.toggle(
          title: 'Center Active App in Recents',
          subtitle: 'Current app stays centered in overview instead of shifting right',
          key: 'ios_anim_recents_centering',
          enabledKey: 'ios_anim_enabled',
        ),
        ItemSpec.toggle(
          title: 'Zero Friction Drag Resistance',
          subtitle: 'Removes rubber-band friction wall when swiping up from apps',
          key: 'ios_anim_zero_friction_drag',
          enabledKey: 'ios_anim_enabled',
        ),
        ItemSpec.toggle(
          title: 'Landscape & Game Optimization',
          subtitle: 'Optimized transition dynamics for landscape and gaming apps',
          key: 'ios_anim_landscape_game_opt',
          enabledKey: 'ios_anim_enabled',
        ),
      ],
    ),
    SectionSpec(
      header: 'Haptics',
      items: [
        ItemSpec.toggle(
          title: 'Refined Haptics',
          subtitle: 'AOSP style crisp haptics for back and recents swipe up gesture',
          key: 'ios_anim_refined_haptics',
          enabledKey: 'ios_anim_enabled',
        ),
        ItemSpec.toggle(
          title: 'Haptics for Sliding Cards',
          subtitle: 'Mild haptic feedback during horizontal swiping in recent apps page',
          key: 'ios_anim_stack_recents_haptics',
          enabledKey: 'ios_anim_enabled',
        ),
      ],
    ),
  ],
);

final callerScreenSettingsSpec = ScreenSpec(
  id: 'caller_screen_settings',
  title: 'Dialer & Caller Screen',
  sections: [
    SectionSpec(
      items: [
        ItemSpec.toggle(
          title: 'Enable iOS Caller Screen',
          subtitle: 'Dual sliders for full-screen incoming calls',
          key: 'caller_screen_enabled',
        ),
      ],
    ),
    SectionSpec(
      header: 'Dimensions & Position',
      items: [
        ItemSpec.slider(
          title: 'Button & Track Size',
          subtitle: 'Scales attend, decline, and end-call buttons uniformly',
          key: 'caller_button_size',
          min: 60,
          max: 120,
          unit: 'dp',
          enabledKey: 'caller_screen_enabled',
        ),
        ItemSpec.slider(
          title: 'Vertical Position Offset',
          subtitle: 'Upward height offset from the bottom of the screen',
          key: 'caller_bottom_offset',
          min: 0,
          max: 150,
          unit: 'dp',
          enabledKey: 'caller_screen_enabled',
        ),
      ],
    ),
    SectionSpec(
      header: 'Dialer App',
      items: [
        ItemSpec.toggle(
          title: 'Swipe Gestures in Dialer',
          subtitle: 'Swipe right to call, swipe left to delete, call history',
          key: 'dialer_recents_swipe_enabled',
        ),
      ],
    ),
  ],
);

ScreenSpec createAppAppearanceSpec(String appName, String appKey) {
  return ScreenSpec(
    id: 'app_appearance_$appKey',
    title: '$appName Appearance',
    sections: [
      SectionSpec(
        header: 'Liquid Glass Engine',
        items: [
          ItemSpec.toggle(
            title: 'Liquid Glass Engine',
            key: 'app_${appKey}_liquid_glass',
            enabledKey: 'app_${appKey}_enabled',
          ),
          ItemSpec.slider(
            title: 'Bar Glass Intensity',
            subtitle: 'Refractive clarity and blur multiplier for nav bar container',
            key: 'app_${appKey}_glass_intensity',
            min: 0.0,
            max: 2.0,
            unit: '%',
            enabledKey: 'app_${appKey}_enabled',
          ),
          ItemSpec.slider(
            title: 'Bar Blur Intensity',
            subtitle: 'Backdrop blur intensity for the nav bar container',
            key: 'app_${appKey}_bar_blur_intensity',
            min: 0.0,
            max: 2.0,
            unit: '%',
            enabledKey: 'app_${appKey}_enabled',
          ),
          ItemSpec.slider(
            title: 'Pill Glass Intensity',
            subtitle: 'Refractive intensity on the active tab indicator pill',
            key: 'app_${appKey}_pill_glass_intensity',
            min: 0.0,
            max: 2.0,
            unit: '%',
            enabledKey: 'app_${appKey}_enabled',
          ),
          ItemSpec.toggle(
            title: 'Pill Chromatic Aberration',
            subtitle: 'Color dispersion fringe around pill refraction edges',
            key: 'app_${appKey}_pill_chromatic_aberration',
            enabledKey: 'app_${appKey}_enabled',
          ),
          ItemSpec.toggle(
            title: 'Pill Shadows & Depth',
            subtitle: 'Drop shadow and inner depth shadow on the indicator pill',
            key: 'app_${appKey}_pill_shadow',
            enabledKey: 'app_${appKey}_enabled',
          ),
        ],
      ),
      SectionSpec(
        header: 'Surface Opacity',
        items: [
          ItemSpec.slider(
            title: 'Bar Container Opacity',
            subtitle: 'Translucent background tint opacity for floating bar',
            key: 'app_${appKey}_container_opacity',
            min: 0.0,
            max: 1.0,
            unit: '%',
            enabledKey: 'app_${appKey}_enabled',
          ),
          ItemSpec.slider(
            title: 'Pill Tint Opacity',
            subtitle: 'Surface tint opacity for the indicator pill',
            key: 'app_${appKey}_pill_container_opacity',
            min: 0.0,
            max: 1.0,
            unit: '%',
            enabledKey: 'app_${appKey}_enabled',
          ),
        ],
      ),
      SectionSpec(
        header: 'Theme & Accent',
        items: [
          ItemSpec.dropdown(
            title: 'Theme',
            key: 'app_${appKey}_theme_mode',
            options: const ['System', 'Light', 'Dark'],
            optionValues: const ['System', 'Light', 'Dark'],
            enabledKey: 'app_${appKey}_enabled',
          ),
          ItemSpec.dropdown(
            title: 'Accent Preset',
            key: 'app_${appKey}_accent',
            options: const ['Auto', 'Green', 'Blue', 'Pink', 'Purple', 'Orange'],
            optionValues: const ['Auto', 'Green', 'Blue', 'Pink', 'Purple', 'Orange'],
            enabledKey: 'app_${appKey}_enabled',
          ),
        ],
      ),
    ],
  );
}

ScreenSpec createAppLayoutSpec(String appName, String appKey) {
  return ScreenSpec(
    id: 'app_layout_$appKey',
    title: '$appName Layout',
    sections: [
      SectionSpec(
        header: 'Shape & Size',
        items: [
          ItemSpec.slider(
            title: 'Corner Radius',
            subtitle: 'Corner rounding radius for floating bar',
            key: 'app_${appKey}_corner_radius',
            min: 0,
            max: 50,
            unit: 'dp',
            enabledKey: 'app_${appKey}_enabled',
          ),
          ItemSpec.slider(
            title: 'Bar Height',
            subtitle: 'Vertical height of floating pill container',
            key: 'app_${appKey}_bar_height',
            min: 40,
            max: 100,
            unit: 'dp',
            enabledKey: 'app_${appKey}_enabled',
          ),
          ItemSpec.slider(
            title: 'Tab Width',
            subtitle: 'Width allocated per tab icon',
            key: 'app_${appKey}_tab_width',
            min: 40,
            max: 120,
            unit: 'dp',
            enabledKey: 'app_${appKey}_enabled',
          ),
          ItemSpec.slider(
            title: 'Bottom Margin',
            subtitle: 'Distance of floating bar above bottom of screen',
            key: 'app_${appKey}_bottom_margin',
            min: 0,
            max: 80,
            unit: 'dp',
            enabledKey: 'app_${appKey}_enabled',
          ),
        ],
      ),
    ],
  );
}

/// Global registry of all available specs.
final List<ScreenSpec> allRegisteredSpecs = [
  keypadIosBloomPhysicsSpec,
  keypadGlassTuningSpec,
  keypadButtonsLayoutSpec,
  keypadPadLayoutSpec,
  keypadStretchPhysicsSpec,
  keypadReleasePhysicsSpec,
  keypadDelaysSpec,
  keypadPhysicsSpec,
  screenUnlockCascadeGlobalsSpec,
  screenUnlockCascadeDockSpec,
  screenUnlockCascadeZDepthSpec,
  screenUnlockCascadeBounceSpec,
  screenUnlockCascadeSettleSpec,
  screenUnlockRadialKineticsSpec,
  screenUnlockRadialDockSpec,
  lockScreenSheetGlassTuningSpec,
  lockScreenSheetLightingSpec,
  lockScreenSheetHomeIndicatorSpec,
  notificationCenterSheetGlassTuningSpec,
  notificationCenterSheetLightingSpec,
  notificationCenterSheetHomeIndicatorSpec,
  morphPhysicsSpec,
  toggleAppearanceSpec,
  togglePhysicsSpec,
  sliderAppearanceSpec,
  sliderPhysicsSpec,
  appTransitionsSpec,
  callerScreenSettingsSpec,
  createAppAppearanceSpec('Instagram', 'instagram'),
  createAppAppearanceSpec('WhatsApp', 'whatsapp'),
  createAppAppearanceSpec('YouTube', 'youtube'),
  createAppLayoutSpec('Instagram', 'instagram'),
  createAppLayoutSpec('WhatsApp', 'whatsapp'),
  createAppLayoutSpec('YouTube', 'youtube'),
];
