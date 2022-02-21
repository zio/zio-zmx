import path from 'path'
import sourcemaps from 'rollup-plugin-sourcemaps'
import resolve from 'rollup-plugin-node-resolve'
import { createHtmlPlugin } from 'vite-plugin-html'

import constants from './constants'

// https://vitejs.dev/config/
export default ({ mode }) => {

    const rollupOutputName = 'app'
    const rollupDir =
      `${constants.projectTargetPath}/rollup`
    const appJS =
      `${constants.sjsGenPath}/main.js`
    const rollupJS =
      `${rollupDir}/${constants.projectName}-${rollupOutputName}.js`
    const appScriptTag =
      `<script type="module" src="${rollupJS}"></script>`

    // We need the project directory, do we can sourcemap our own project sources during development
    const prjDir = path.resolve("../..");

    // const sourcePathMappings = [
    //     // The first settings correct sourcemaps that have accidentally been published with incorrect code references
    //     // and remaps those to vaild githup raw urls.
    //     // It seems that changes to these mappings require a build rather than running just the vite server
    //     { from: "https:/raw", to: "https://raw" },
    //     { from: "file:/home/runner/work/animus/animus", to: "https://raw.githubusercontent.com/kitlangton/animus/v0.1.9" },
    //     { from: "file:/home/runner/work/zio/zio", to: "https://raw.githubusercontent.com/zio/zio/v1.0.9" },
    //     // We have to remap the client sources, so that the vite root directory does not collide with any of the
    //     // directories containing the Scala sources, otherwise vite cant serve the dev page
    //     { from: 'file:' + prjDir + '/client/js/src', to: "/cltjs" },
    //     { from: 'file:' + prjDir + '/client/shared/src', to: "/cltshared" },
    //     { from: 'file:' + prjDir, to: "" }
    // ]

    return {
        build: {
            target: 'esnext',
            sourcemap: true,
            rollupOptions: {
                //external: [/node_modules/],
                input: {
                    [rollupOutputName]: appJS
                },
                plugins: [resolve()],
                output: {
                    dir: rollupDir,
                    entryFileNames: `${constants.projectName}-[name].js`,
                    format: 'es'
                    // sourcemapPathTransform: (relativeSourcePath, _sourcemapPath) => {
                    //     // will replace relative paths with absolute paths
                    //     var srcPath = relativeSourcePath.replace(`../scala-${scalaVersion}/${appDir}/`, '');
                    //     var result = srcPath
                    //     var mapped = false
                    //     sourcePathMappings.forEach((value, _idx, _array) => {
                    //         if (!mapped && result.startsWith(value.from)) {
                    //             mapped = true
                    //             result = result.replace(value.from, value.to);
                    //         }
                    //     })
                    //     //console.log(result)
                    //     return result
                    // },
                }
            }
        },
        publicDir: 'public',
        plugins: [
            createHtmlPlugin({
                minify: constants.isInProdMode,
                inject: {
                  data: {
                    appScriptTag
                  },
                },
            }),
        ],
        resolve: {
            alias: {
                'stylesheets': path.resolve(__dirname, constants.cssPath),
            }
        }
    }
}
