import path from 'path'
import sourcemaps from 'rollup-plugin-sourcemaps'
import { minifyHtml, injectHtml } from 'vite-plugin-html'

const scalaVersion = '2.13'
// const scalaVersion = '3.0.0-RC3'

// https://vitejs.dev/config/
export default ({ mode }) => {
    const appDir = `zio-zmx-client-${mode === 'production' ? 'opt' : 'fastopt'}`
    const mainJS = `./target/scala-${scalaVersion}/${appDir}/main.js`
    const script = `<script type="module" src="./target/rollup/zio-zmx-client-app.js"></script>`

    // We need the project directory, do we can sourcemap our own project sources during development
    const prjDir = path.resolve("../..");

    const BreakException = {}

    const sourcePathMappings = [
        // The first settings correct sourcemaps that have accidentally been published with incorrect code references 
        // and remaps those to vaild githup raw urls. 
        // It seems that changes to these mappings require a build rather than running just the vite server 
        { from: "https:/raw", to: "https://raw" },
        { from: "file:/home/runner/work/animus/animus", to: "https://raw.githubusercontent.com/kitlangton/animus/v0.1.9" },
        { from: "file:/home/runner/work/zio/zio", to: "https://raw.githubusercontent.com/zio/zio/v1.0.9" },
        // We have to remap the client sources, so that the vite root directory does not collide with any of the 
        // directories containing the Scala sources, otherwise vite cant serve the dev page
        { from: 'file:' + prjDir + '/client/js/src', to: "/cltjs" },
        { from: 'file:' + prjDir + '/client/shared/src', to: "/cltshared" },
        { from: 'file:' + prjDir, to: "" }
    ]

    return {
        build: {
            target: 'esnext',
            sourcemap: true,
            rollupOptions: {
                input: {
                    app: mainJS
                },
                plugins: [sourcemaps()],
                output: {
                    dir: 'target/rollup',
                    entryFileNames: 'zio-zmx-client-[name].js',
                    format: 'es',
                    sourcemapPathTransform: (relativeSourcePath, _sourcemapPath) => {
                        // will replace relative paths with absolute paths
                        var srcPath = relativeSourcePath.replace(`../scala-${scalaVersion}/${appDir}/`, '');
                        var result = srcPath
                        var mapped = false
                        sourcePathMappings.forEach((value, _idx, _array) => {
                            if (!mapped && result.startsWith(value.from)) {
                                mapped = true
                                result = result.replace(value.from, value.to);
                            }
                        })
                        console.log(result)
                        return result
                    },
                }
            }
        },
        server: {
            fs: {
                strict: false,
                allow: ['../..']
            },
            proxy: {
                '/api': {
                    target: 'http://localhost:8088',
                    changeOrigin: true,
                    rewrite: (p) => p.replace(/^\/api/, '')
                },
            }
        },
        publicDir: 'public',
        plugins: [
            ...(process.env.NODE_ENV === 'production' ? [minifyHtml(),] : []),
            injectHtml({
                injectData: {
                    script
                }
            })
        ],
        resolve: {
            alias: {
                '/@': prjDir,
                'stylesheets': path.resolve(__dirname, './frontend/src/main/static/stylesheets'),
            }
        }
    }
}