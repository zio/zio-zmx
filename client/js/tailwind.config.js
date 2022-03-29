const constants = require('./constants.ts')
const jsPattern =
  `${constants.sjsGenPath}/*.js`

module.exports = {
  content: [
    'index.html',
    jsPattern,
  ],
  theme: {
    extend: {}
  },
  variants: {
    extend: {},
  },
  safelist: [
    // Make sure to include all tw rows and cols for the dynamic dashboard layout 
    { pattern: /grid-(rows|cols)-.+/ },
  ],
  plugins: [
    require('@tailwindcss/forms'),
    require('@tailwindcss/typography'),
    require('@tailwindcss/aspect-ratio'),
    require('daisyui'),
  ],
}