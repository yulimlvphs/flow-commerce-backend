/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        sf: {
          black: '#111111',
          dark: '#1a1a1a',
          accent: '#E8344E',
          cream: '#FAFAF8'
        }
      },
      fontFamily: {
        sans: ['Inter', 'Pretendard', 'sans-serif']
      },
      keyframes: {
        marquee: {
          '0%': { transform: 'translateX(100%)' },
          '100%': { transform: 'translateX(-100%)' }
        }
      },
      animation: {
        marquee: 'marquee 20s linear infinite'
      }
    }
  },
  plugins: []
}
