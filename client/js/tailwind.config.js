const constants = require('./constants.ts')
const jsPattern =
  `${constants.sjsGenPath}/*.js`

module.exports = {
  content: [
    'index.html',
    jsPattern
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
