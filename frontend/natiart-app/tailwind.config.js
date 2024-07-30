/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  darkMode: 'media', // or 'media' or 'class'
  theme: {
    extend: {
      colors: {
        'primary': {
          DEFAULT: '#D4A59A',
          'light': '#E3C1B8',
          'lighter': '#F9EBE0',
          'dark': '#C89F94',
        },
        'secondary': {
          DEFAULT: '#4A4A4A',
          'light': '#888888',
        },
        'background': '#FDF5E6',
        'error': '#ef4444'
      },
      fontFamily: {
        'sans': ['Poppins', 'sans-serif'],
      },
      fontSize: {
        'xs': '0.75rem',
        'sm': '0.875rem',
        'base': '1rem',
        'lg': '1.125rem',
        'xl': '1.25rem',
        '2xl': '1.5rem',
        '3xl': '1.875rem',
        '4xl': '2.25rem',
      },
      boxShadow: {
        'input': '0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06)',
      },
      borderRadius: {
        'md': '0.375rem',
      },
    },
  },
  variants: {
    extend: {},
  },
  plugins: [],
};
