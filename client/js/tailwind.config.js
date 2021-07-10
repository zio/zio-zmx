const scalaversion = require('./scala-version')

module.exports = (api) => {
  const scalajsMode = api.mode === 'production' ? 'opt' : 'fastopt'

  return {
    purge: {
      enabled: true,
      content: [
        './index.html',
        `./target/scala-${scalaversion}'/${scalajsMode}/*.js`,
        './src/main/static/**/*.html'
      ]
    },
    darkMode: false,
    theme: {
      extend: {},
    },
    variants: {
      extend: {},
    },
    plugins: [
      require('@tailwindcss/forms'),
      require('@tailwindcss/typography'),
      require('@tailwindcss/aspect-ratio')
    ],
  }
}
