module.exports = {
  content: [
    'index.html',
    './target/scala-2.13/zio-zmx-client-fastopt/*.js'
  ],
  theme: {
    extend: {},
  },
  variants: {
    extend: {},
  },
  plugins: [
    require('@tailwindcss/forms'),
    require('@tailwindcss/typography'),
    require('@tailwindcss/aspect-ratio'),
    require('daisyui'),
  ],
}
