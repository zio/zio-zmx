---
id: client_index
title: "WIP: Scala JS Client"
---

Even though the normal use case for ZMX is to use one of supported metric back ends, the ZMX team has decided 
to provide a simplified client using ScalaJS, so users can explore metrics capturing and visual presentation 
without having to install the pre-requisites for one of the back ends. 

> The ScalaJS client is not designed as a replacement for one of the dashboard implementations offering a 
> much larger feature set. It is designed to quickly visualize ZMX metrics.

## ZMX client technology stack

### Server side

The ZMX code base now provides a `MetricNotifier` that can be mixed into an instrumented ZIO 2 application. 
The MetricNotifier manages subscriptions to metrics and provides a stream of updates. Clients can use the 
notifier API to subscribe metrics and receive updates at regular intervals. With this API only the metrics 
that have subscriptions are evaluated at the points in time when the data is needed. Overall this reduces 
the monitoring overhead significantly. 

### Client side

The Scala JS client uses the `MetricNotifier` API to implement a WebSocket protocol between the client and the 
monitored application. 

The client is built on top of [Laminar](https://laminar.dev/), so the stream of inbound messages is turned into 
a airstream, which is tightly integrated into Laminar. 

We have built a simple framework to create metrics dashboard. The dashboard always uses the entire client space 
within the browser and starts with an empty dashboard panel. The user can use the `Split Horizontally` and 
`Split Vertically` buttons to change the dashboard layout. 

An empty panel can be configured by clicking on the `+` button in the middle of the panel. 

The configuration dialog displays the currently selected metrics for the panel and offers all known metrics 
for selection. A metric can be added to the panel only once, so once it is selected it will be removed from the 
metrics available for selection. Any number of metrics can be added to the selection. Once a metric is selected it can be removed from the selection by clicking on the label in the `Configured Metrics`section. 

[Configure Metrics](/zio-zmx/img/jsclient-config.png)

Once the selection is confirmed, the panel dashboard will show the metrics graph using a [Vega Lite](https://vega.github.io/vega-lite/) specification. At any point, the user can use the buttons located at the top right 
of the panel to invoke either the config dialog or the editor for the vega specification. 

The config dialog allows to change the selected metrics for the dashboard, the collection interval and the number of samples kept for the graphs. 

[Simple Vega Editor](/zio-zmx/img/jsclient-vegaedit.png)

The vega edit dialog just offers a text panel to edit the Vega Lite specification. By clicking `Edit in Vega` 
the specification will be opened in the Vega Lite editor, which allows to edit the specification interactively 
and validate to resulting graph. Once the editing is done, the spec can be copied back into the ZMX client and 
the dashboard will use the edited specification to render the graph.

[Vega Lite Editor](/zio-zmx/img/vegalite-edit.png)

## Working on the ZMX client 

To work on the client, besides `sbt` `node` has to be installed on the development machine. The directory `client/js`
is the base directory for all Node JS related tasks. 

### Prepare the environment

1. At first, run `yarn install` within `client/js`. 
1. Build the tailwind based CSS with `npx tailwind -i src/main/index.css -o ./target/rollup/main.css`

### Build and run the instrumented server / client

For now we are using `uzhttp` on the server side to realize the Web Socket protocol required by the client. 
This is a temporary solution until ZIO HTTP is available for ZIO 2. 

At the moment, uzhttp has no official release for ZIO 2 either, so as a preparational step, you have 
to checkout the ZIO 2 version from https://github.com/blended-zio/uzhttp/tree/zio2 and use sbt to publish 
uzhttp for ZIO 2 locally. 

1. Start the instrumented Server with with web socket server from the ZMX checkout directory with `sbt clientJVM/run`
1. In another shell start a continuous compile of the Scala JS client code by starting a sbt shell and execute `~clientJS/fastOptJS`. This will recompile the Scala JS code upon each save of a related source file.
1. In yet another shell, from within `client/js` start `npx vite build -m development --watch`. This will repackage
   the web application upon on each change. 
1. In yet another shell, from within `client/js` start `npx vite`. This will start a local HTTP server on port 
   3000 picking up the currently packaged application. `http://localhost:3000` will now display the client. 

   In case you want to use a browser on a different machine than the machine where you have started the vite 
   server, you have to start the vite server with `npx vite --host 0.0.0.0`, so that the vite server starts to 
   listen on all network interfaces.    

### Ideas for further development (non-exhaustive)

1. It might make sense to change the diagram type, for example a bar chart or pie chart might make sense for sets. Here we might simply maintain a curated list of useful Vega lite Specs. 
1. Ideally, the configuration for each diagram could be captured in a JSON serializable case class, so that we could
   store the state of all diagrams currently displayed within the web page to JSON. We could have a dialog where we 
   could simply display the current JSON to copy it into a file and another dialog to paste a JSON config for setting 
   the diagram state. Potentially we could use local browser storage for storing and retrieving JSON. 
   




