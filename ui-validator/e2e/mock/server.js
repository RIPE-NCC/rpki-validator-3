const jsonServer = require('json-server');
const server = jsonServer.create();


server.get('/api/roas', (req, res) => {

  if (req.query.sortBy === 'asn' && req.query.sortDirection === 'asc') {
    res.jsonp(require('./data/roas/roas-response-sortByASN-asc.json'));
  } else if (req.query.sortBy === 'asn' && req.query.sortDirection === 'desc') {
    res.jsonp(require('./data/roas/roas-response-sortByASN-desc.json'));
  } else if (req.query.sortBy === 'prefx' && req.query.sortDirection === 'asc') {
    res.jsonp(require('./data/roas/roas-response-sortByPREFIX-asc.json'));
  } else if (req.query.sortBy === 'prefix' && req.query.sortDirection === 'desc') {
    res.jsonp(require('./data/roas/roas-response-sortByPREFIX-desc.json'));
  } else if (req.query.sortBy === 'ta' && req.query.sortDirection === 'asc') {
    res.jsonp(require('./data/roas/roas-response-sortByTRUSTANCHOR-asc.json'));
  } else if (req.query.sortBy === 'ta' && req.query.sortDirection === 'desc') {
    res.jsonp(require('./data/roas/roas-response-sortByTRUSTANCHOR-desc.json'));
  }

  else if (req.query.search === 'Bobo') {
    res.jsonp(require('./data/roas/roas-response-search.json'));
  }

  else if (req.query.pageSize === '10') {
    res.jsonp(require('./data/roas/roas-response-0-10.json'));

  } else if (req.query.pageSize === '25') {
    if (req.query.startFrom === '0') {
      res.jsonp(require('./data/roas/roas-response-0-25.json'));
    } else if (req.query.startFrom === '25') {
      res.jsonp(require('./data/roas/roas-response-25-25.json'));
    }

  } else if (req.query.pageSize === '50') {
    res.jsonp(require('./data/roas/roas-response-0-50.json'));
  }
});

server.get('/api/trust-anchors', (req, res) => {
  res.jsonp(require('./data/trust-anchors/trust-anchors.json'));
});

server.get('/api/export.csv', (req, res) => {
  res.jsonp('{}')
});

server.get('/api/export.json', (req, res) => {
  res.jsonp('{}')
});

server.get('/api/trust-anchors/statuses', (req, res) => {
  res.jsonp(require('./data/trust-anchors/ta-statuses.json'));
});

server.get('/api/trust-anchors/3268', (req, res) => {
  res.jsonp(require('./data/monitor/monitoring.json'));
});

server.get('/api/trust-anchors/monitor/3268', (req, res) => {
  res.jsonp(require('./data/monitor/monitoring.json'));
});

server.get('/api/trust-anchors/3268/validation-checks', (req, res) => {
  res.jsonp(require('./data/monitor/validation-details.json'));
});

server.get('/api/rpki-repositories', (req, res) => {
  res.jsonp(require('./data/monitor/repositories.json'));
});

server.get('/api/rpki-repositories/statuses/3268', (req, res) => {
  res.jsonp(require('./data/monitor/repositories-statuses.json'));
});

server.get('/api/bgp/validity', (req, res) => {
  res.jsonp(require('./data/announcement-preview/announcement-preview-AS3215-2.0.0.0_16.json'));
});

server.listen(3000, () => {
  console.log('Mock server running on 3000');
});


