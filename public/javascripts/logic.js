/* specify mapbox token */
mapboxgl.accessToken = '';

/* mapbox object with default configuration */
const map = new mapboxgl.Map({
    container: 'map',
    style: 'mapbox://styles/mapbox/light-v9',
    center: [-96.35, 39.5],
    zoom: 3.5,
    maxZoom: 17,
    minZoom: 0
});

/* WebSocket object */
let socket;
/* zoom level after last time */
let zoomLevel = Math.floor(map.getZoom());
/* id for the timer used to send heartbeat pack of WebSocket */
let timerId = 0;
/* keyword of query */
let query = "";
var layer_id_list = [];
var cnt = 0;
let layerOpacity = document.getElementById("opacity").value / 1000;
let previousBounds = getScreenStatus();
/**
 * get the screen status and store the result in a json
 */
function getScreenStatus() {
    let bounds = map.getBounds();
    let ne = bounds.getNorthEast();
    let sw = bounds.getSouthWest();
    return {
        "currentZoom": Math.floor(map.getZoom()),
        "maxLng": ne.lng,
        "maxLat": ne.lat,
        "minLng": sw.lng,
        "minLat": sw.lat
    };
}

/**
 binding function to move end action
 */
map.on("moveend", () => {
    statusChange("move");
});

/**
 * binding function to zoom end action
 */
map.on('zoomend', () => {
    statusChange("zoom");
});

/**
 * get the radius option
 */
function getRadiusOption() {

    const opts = {
        "rad40": 40,
        "rad80": 80,
    };
    return opts[document.getElementById("radius").value];
}

/**
 * get the clustering algorithm
 */
function getClusteringAlgorithm() {
    const algorithms = {
        "HGC": 0,
        "I-KMeans": 1,
        "KMeans": 2
    };
    return algorithms[document.getElementById("clusteringAlgorithm").value];
}

/**
 * get the bundling algorithm
 */
function getBundlingAlgorithm() {
 const algorithms = {
        "FDEB": 0,
        "IFDEB": 1,
        "FE": 2
    };
    return algorithms[document.getElementById("bundlingAlgo").value];
}

/**
 * get the choice for certain checkbox with id = elementId
 * @param elementId id of the target html element
 */
function getChoice(elementId) {
    let choices = {
        true: 1,
        false: 0
    };
    return choices[document.getElementById(elementId).checked];
}


/**
 * construct and send the request's json string
 */
function sendingRequest(zoom, newQuery) {
    if (socket === undefined) return;
    previousBounds = getScreenStatus();
    let minLng = previousBounds['minLng'];
    let minLat = previousBounds['minLat'];
    let maxLng = previousBounds['maxLng'];
    let maxLat = previousBounds['maxLat'];
    if (zoom === undefined) zoom = previousBounds['currentZoom'];
    const clusteringAlgorithm = getClusteringAlgorithm();
    const bundlingAlgorithm = getBundlingAlgorithm();
    let clusteringControl = getChoice("cluster");
    let bundlingControl = getChoice("bundle");
    let cutControl = getChoice("treeCut");
    let pointStatus = getChoice("point");
    let edgeStatus = getChoice("edge");
    let radius = getRadiusOption();
    let sendingObj = {
        query: query,
        lowerLongitude: minLng,
        upperLongitude: maxLng,
        lowerLatitude: minLat,
        upperLatitude: maxLat,
        clusteringAlgorithm: clusteringAlgorithm,
        bundlingAlgorithm: bundlingAlgorithm,
        bundling: bundlingControl,
        treeCut: cutControl,
        clustering: clusteringControl,
        zoom: zoom,
        pointStatus: pointStatus,
        edgeStatus: edgeStatus,
        newQuery: newQuery,
        radius: radius
    };
    const sendingJSON = JSON.stringify(sendingObj);
    socket.send(sendingJSON);
}

/**
 * Draw the layer after receiving edge data
 * @param data received edge data
 */
function receiveEdges(data) {
    const edgeLayer = new MapboxLayer({
        id: 'edge' + cnt,
        type: LineLayer,
        opacity: layerOpacity,
        updateTriggers: {
            opacity: [layerOpacity]
        },
        data: data,
        getSourcePosition: d => d.from,
        getTargetPosition: d => d.to,
        getWidth: d => {
            let temp = Math.min(15, Math.ceil(Math.pow(d.width, 1 / 2)));
            return Math.max(temp, 1);
        },
        getColor: d => d.color
    });
    add_id_2_list('edge' + cnt);
    map.addLayer(edgeLayer);
}

function add_id_2_list(id) {
    layer_id_list.push(id);
    cnt++;
}
/**
 * Draw the layer after receiving cluster data
 * @param data received cluster data
 */
function receiveClusterPoints(data) {
    const clusterLayer = new MapboxLayer({
        id: 'cluster',
        type: ScatterplotLayer,
        data: data,
        pickable: true,
        opacity: 0.8,
        stroked: false,
        filled: true,
        radiusScale: 100,
        radiusMinPixels: 1,
        radiusMaxPixels: 25,
        getPosition: d => d.coordinates,
        getRadius: d => d.size,
        getFillColor: d => [0, 0, 0],
    });

      const labelLayer = new MapboxLayer({
        id: 'label',
        type: TextLayer,
        data: data,
        pickable: true,
        getPosition: d => d.coordinates,
        getText: d => (d.size).toString(),
        getSize: d => {
                 let temp = Math.min(20, Math.ceil(Math.pow(d.size, 1 / 2)));
                 return Math.max(temp, 1);
                      }
      });

    removeClusterLayer();
    map.addLayer(clusterLayer);
}

/**
 * Updating the statistics data of points
 * @param pointsJson the json containing points data
 */
function updatePointsStats(pointsJson) {
    document.getElementById('repliesCnt').innerHTML = "Reply Tweets Count: " + pointsJson['repliesCnt'] + " / 20,023,731";
    document.getElementById('pointsCnt').innerHTML = "Points Count: " + pointsJson['pointsCnt'];
    document.getElementById('clustersCnt').innerHTML = "Clusters Count: " + pointsJson['clustersCnt'];
}

/**
 * Updating the statistics data of edges
 * @param edgesJson the json containing edges data
 */
function updateEdgesStats(edgesJson) {
    document.getElementById('edgesCnt').innerHTML = "Edges Count: " + edgesJson['edgesCnt'];
    document.getElementById('bundledEdgesCnt').innerHTML = "Bundled Edges Count: " + (edgesJson['edgesCnt'] - edgesJson['isolatedEdgesCnt']);
}

/**
 * remove edge layer from the map
 */
function removeEdgeLayer() {
    for (var i = layer_id_list.length - 1; i >= 0; i--) {
        map.removeLayer(layer_id_list[i]);
    }
    layer_id_list = [];
    cnt = 0;
}

/**
 * remove cluster layer from the map
 */
function removeClusterLayer() {
    if (map.getLayer('cluster') !== undefined) {
        map.removeLayer('cluster');
    }
    if (map.getLayer('label') !== undefined) {
            map.removeLayer('label');
    }
}

/**
 * remove both layers
 */
function removeLayer() {
    removeEdgeLayer();
    removeClusterLayer();
}

/**
 * do the bundling in the front end
 */
function doBundling(pointData, edgeData, json) {
    //bundle
    var fbundling = d3.ForceEdgeBundling().step_size(0.02)
        .nodes(pointData)
        .edges(edgeData);
    var results = fbundling();
    //format the output result
    var result = formatBundledData(results);
    //var result = [].concat.apply([], results);
    //call receive edges and node
    removeEdgeLayer();
    updatePointsStats(json);
    updateEdgesStats(json);
    receiveEdges(result);
}

/**
 * format the result from the bundling to fit deckgl library format
 */
function formatBundledData(results) {
    var t0 = performance.now();
    var result = [];
    for (var j = 0; j < results.length; j++) {
        var edge = results[j];
        for (var i = 0; i < edge.length - 1; i++) {
            var fromPoint = [];
            fromPoint.push(edge[i].x);
            fromPoint.push(edge[i].y);
            var toPoint = [];
            toPoint.push(edge[i + 1].x);
            toPoint.push(edge[i + 1].y);
            var edgeObj = {
                from: fromPoint,
                to: toPoint,
                width: 1
            };
            result.push(edgeObj);
        }
    }
    var t1 = performance.now();
    console.log("formatting " + (t1 - t0));
    return result;
}

/**
 * draw graph function associated with the show button in the main page
 */
function drawGraph() {
    removeLayer();
    query = document.getElementById("keyword-textbox").value;
    socket = new WebSocket("ws://localhost:9000/replies");

    /**
     * socket on open function, sending the first batch request of the incremental query
     */
    socket.onopen = function() {
        // with the zoom level unspecified
        sendingRequest(undefined, true);
        keepAlive();
    };


    /**
     * function for socket open event, sending the first batch request of the incremental query
     * @param event the event associated with the message receiving action, which carries the data
     */
    socket.onmessage = function(event) {
        let json = JSON.parse(event.data);
        let pointStatus = json['pointStatus'];
        let edgeStatus = json['edgeStatus'];
        let pointData = JSON.parse(json['pointData']);
        let edgeData = JSON.parse(json['edgeData']);
        if (getBundlingAlgorithm() == 2) {
            doBundling(pointData, edgeData, json);
        } else {
            // cluster response result
            if (pointStatus === 1) {
                updatePointsStats(json);
                receiveClusterPoints(pointData);
            }
            // edge response result
            if (edgeStatus === 1) {
                removeEdgeLayer();
                updateEdgesStats(json);
                receiveEdges(edgeData);
            }
        }
    };
}

function dnlScreenShot(){
        let div = document.getElementById("svg");
        html2canvas(div).then(canvas => {
          document.body.appendChild(canvas);
          let a = document.createElement('a');
          // toDataURL defaults to png, so we need to request a jpeg, then convert for file download.
          a.href = canvas.toDataURL("image/png").replace("image/png", "image/octet-stream");
          a.download = 'screenshot.png';
          a.click();
        });
}
/**
 * handler function for all events associated with the checkbox / map
 * @param changeEvent
 */
function statusChange(changeEvent) {
//check if the current status is the same as before, then don't send a new request
let currentBounds = getScreenStatus();
if(changeEvent === 'move' && _.isEqual(currentBounds, previousBounds))
return;
    let pointStatus = getChoice("point");
    let clusterStatus = getChoice("cluster");
    let edgeStatus = getChoice("edge");
    let bundleStatus = getChoice("bundle");
    let treeCutStatus = getChoice("treeCut");
    let pointDraw = 0;
    let edgeDraw = 0;
    newQuery = false;
    if (changeEvent === 'point' || changeEvent === 'cluster' || changeEvent === 'treeCut') {
        // select cluster checkbox without select point, uncheck the cluster and send alert
        if (clusterStatus && !pointStatus) {
            alert("Please check cluster with points.");
            $('#cluster').prop('checked', false);
            clusterStatus = 0;
        }
        // select treecut checkbox without select cluster, uncheck the treecut and send alert
        if (!clusterStatus && treeCutStatus) {
            alert("Please select tree cut with cluster and edge.");
            $('#treeCut').prop('checked', false);
        }
        // if cluster or point checkbox is selected, call the function to send cluster request
        // otherwise remove the cluster layer
        if (clusterStatus || pointStatus)
            pointDraw = 1;
        else
            removeClusterLayer();
        // when conversion happens between cluster and point, edge need to be redrawed, call the function to send edge request
        if (edgeStatus)
            edgeDraw = 1;
        else
            removeEdgeLayer();
    }
    if (changeEvent === 'edge' || changeEvent === 'bundle' || changeEvent === 'treeCut') {
        // select bundle checkbox without select edge, uncheck the bundle and send alert
        if (bundleStatus && !edgeStatus) {
            alert("Please check bundle with edge.");
            $('#bundle').prop('checked', false);
            bundleStatus = 0;
        }
        // select tree cut checkbox without select edge, uncheck the tree cut and send alert
        if (!edgeStatus && treeCutStatus) {
            alert("Please select tree cut with cluster and edge.");
            $('#treeCut').prop('checked', false);
            treeCutStatus = 0;
        }
        // if bundle or edge or tree cut checkbox is selected, call the function to send edge request
        // otherwise remove the edge layer
        if (bundleStatus || edgeStatus || treeCutStatus)
            edgeDraw = 1;
        else
            removeEdgeLayer();
    }
    // for map status change event, call the function to send edge request, meanwhile record the current zoom level
    // the recorded zoom level will be used for the detection of the zoom level change
    if (changeEvent === 'move' || (changeEvent === 'zoom' && zoomLevel !== currentBounds['currentZoom'])) {
        if (pointStatus)
            pointDraw = 1;
        if (edgeStatus)
            edgeDraw = 1;
        zoomLevel = currentBounds['currentZoom'];
        if(getClusteringAlgorithm() !== 0)
            newQuery = true;
    }
    let zoom = undefined;
    if (pointDraw){
    let clusteringControl = getChoice("cluster");
        // if cluster is not selected, directly use the lowest zoom level
        if (clusteringControl === 0) {
            zoom = 18;
        }
    }
    sendingRequest(zoom, newQuery);
}

/**
 * send heartbeat package to keep the connection alive
 */
function keepAlive() {
    const timeout = 200000;
    if (socket.readyState === WebSocket.OPEN) {
        socket.send('');
    }
    timerId = setTimeout(keepAlive, timeout);
}

/**
 * change the opacity of edges
 */
function changeLayerOpacity(newOpacity) {
    layerOpacity = newOpacity / 1000;
}
