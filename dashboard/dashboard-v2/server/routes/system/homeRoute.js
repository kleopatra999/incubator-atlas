'use strict';

module.exports = function(app) {

    app.get('/', function(req, res) {
        res.render('index', {
            renderErrors: {}, //req.flash('error')
            app: app.config.app
        });
    });
};