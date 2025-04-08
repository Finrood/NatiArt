/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  darkMode: 'media', // Sticking with 'media' as it’s great for automatic dark mode
  theme: {
    extend: {
      colors: {
        // Existing primary, secondary, etc., remain unchanged
        primary: {
          DEFAULT: 'rgb(var(--primary) / <alpha-value>)',
          light: 'rgb(var(--primary-light) / <alpha-value>)',
          lighter: 'rgb(var(--primary-lighter) / <alpha-value>)',
          dark: 'rgb(var(--primary-dark) / <alpha-value>)',
          contrast: 'rgb(var(--primary-contrast) / <alpha-value>)',
        },
        secondary: {
          DEFAULT: 'rgb(var(--secondary) / <alpha-value>)',
          light: 'rgb(var(--secondary-light) / <alpha-value>)',
          dark: 'rgb(var(--secondary-dark) / <alpha-value>)',
          contrast: 'rgb(var(--secondary-contrast) / <alpha-value>)',
        },
        error: '#ef4444',
        success: '#22c55e',
        warning: '#eab308',
        info: '#3b82f6',
        surface: {
          DEFAULT: 'rgb(var(--surface) / <alpha-value>)',
          variant: 'rgb(var(--surface-variant) / <alpha-value>)',
          contrast: 'rgb(var(--on-surface) / <alpha-value>)',
        },
        background: {
          DEFAULT: 'rgb(var(--background) / <alpha-value>)',
          variant: 'rgb(var(--background-variant) / <alpha-value>)',
        },
        accent: {
          DEFAULT: 'rgb(var(--accent) / <alpha-value>)',
          contrast: 'rgb(var(--accent-contrast) / <alpha-value>)',
        },
        // New: muted text color for descriptions
        'text-muted': 'rgb(var(--text-muted) / <alpha-value>)', // Define --text-muted in CSS
      },
      fontFamily: {
        sans: ['Poppins', 'sans-serif'],
        serif: ['Playfair Display', 'serif'],
        mono: ['Fira Code', 'monospace'],
        display: ['Bebas Neue', 'cursive'],
      },
      fontSize: {
        '2xs': '0.625rem',
        xs: '0.75rem',
        sm: '0.875rem',
        base: '1rem',
        lg: '1.125rem',
        xl: '1.25rem',
        '2xl': '1.5rem',
        '3xl': '1.875rem',
        '4xl': '2.25rem',
        '5xl': '3rem',
        '6xl': '4rem',
      },
      spacing: {
        '4.5': '1.125rem',
        '18': '4.5rem',
        '22': '5.5rem',
        '30': '7.5rem',
        '112': '28rem',
        '128': '32rem',
      },
      boxShadow: {
        sm: '0 1px 2px 0 rgb(0 0 0 / 0.05)',
        DEFAULT: '0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1)',
        md: '0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1)',
        lg: '0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1)',
        xl: '0 20px 25px -5px rgb(0 0 0 / 0.1), 0 8px 10px -6px rgb(0 0 0 / 0.1)',
        inner: 'inset 0 2px 4px 0 rgb(0 0 0 / 0.05)',
        input: '0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06)',
        focus: '0 0 0 3px rgb(var(--primary) / 0.15)',
        // New: elevated shadow for product cards
        'elevated': '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)',
      },
      borderRadius: {
        none: '0',
        sm: '0.125rem',
        DEFAULT: '0.25rem',
        md: '0.375rem',
        lg: '0.5rem',
        xl: '0.75rem',
        '2xl': '1rem',
        '3xl': '1.5rem',
        full: '9999px',
      },
      transitionProperty: {
        height: 'height',
        spacing: 'margin, padding',
        size: 'width, height',
        // New: all properties for smoother hover effects
        'all': 'all',
      },
      keyframes: {
        fadeIn: {
          // Enhanced: added slight vertical movement
          '0%': { opacity: '0', transform: 'translateY(10px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        slideUp: {
          '0%': { transform: 'translateY(100%)' },
          '100%': { transform: 'translateY(0)' },
        },
        spin: {
          '100%': { transform: 'rotate(360deg)' },
        },
      },
      animation: {
        // Updated: adjusted timing for elegance
        'fade-in': 'fadeIn 0.5s ease-out',
        slideUp: 'slideUp 0.3s ease-out',
        spin: 'spin 1s linear infinite',
      },
      opacity: {
        15: '0.15',
        30: '0.3',
        85: '0.85',
      },
      zIndex: {
        1: '1',
        60: '60',
        70: '70',
        80: '80',
        90: '90',
        100: '100',
      },
    },
  },
  variants: {
    extend: {
      scale: ['active', 'group-hover', 'hover'], // Added hover for card effects
      rotate: ['group-hover'],
      opacity: ['disabled'],
      cursor: ['disabled'],
      translate: ['hover'], // Added for subtle lifts
    },
  },
  plugins: [
    require('@tailwindcss/typography'),
    require('@tailwindcss/forms'),
    require('@tailwindcss/aspect-ratio'),
    require('@tailwindcss/line-clamp'),
  ],
};
