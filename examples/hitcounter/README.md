# Hitcounter

A simple hit counter application to demonstrate use of
[enduro](https://github.com/alandipert/enduro/) for persistence via
PostgreSQL on [Heroku](http://www.heroku.com/).

## Usage

### Deployment to Heroku

See [Building a Database-Backed Clojure Web
Application](https://devcenter.heroku.com/articles/clojure-web-application)
for instructions on installing and configuring the Heroku tools and
deploying database-backed Clojure applications.

Then, deploy this application as per the article's
[Deploy](https://devcenter.heroku.com/articles/clojure-web-application#deploy)
instructions.

### Running Locally

If you are running PostgreSQL locally, you may run the application on
your machine with a command like:

    DATABASE_URL="postgresql://localhost:5432/your-db" foreman start

Replace `your-db` and the port number as appropriate.