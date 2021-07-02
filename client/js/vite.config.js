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

    const BreakException = {}

    const sourcePathMappings = [
        { from: "https:/raw", to: "https://raw" },
        { from: "file:/home/runner/work/animus/animus", to: "https://raw.githubusercontent.com/kitlangton/animus/v0.1.9" }
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
                    sourcemapPathTransform: (relativeSourcePath, sourcemapPath) => {
                        // will replace relative paths with absolute paths
                        var srcPath = relativeSourcePath.replace(`../scala-${scalaVersion}/${appDir}/`, '');
                        var result = srcPath
                        try {
                            sourcePathMappings.forEach((value, idx, array) => {
                                if (result.startsWith(value.from)) {
                                    result = result.replace(value.from, value.to);
                                    throw BreakException;
                                }
                            })
                        } catch (e) {
                            if (e != BreakException) throw e
                        }
                        console.log(result + " -- " + srcPath);
                        return result
                    },
                }
            }
        },
        server: {
            proxy: {
                '/api': {
                    target: 'http://localhost:8088',
                    changeOrigin: true,
                    rewrite: (p) => p.replace(/^\/api/, '')
                },
            }
        },
        publicDir: './src/main/static/public',
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
                'stylesheets': path.resolve(__dirname, './frontend/src/main/static/stylesheets'),
            }
        }
    }
}