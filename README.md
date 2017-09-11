# Film Ratings

Just messing around with streams and stuff.

## Usage
Run the project with `sbt run`. Then visit http://localhost:8080/generate to generate the data (this takes a long time 5min+). 

Once you see "Done" then visit http://localhost:8080 to see the results. By default the results only show those above 80 points, sorted by Metacritic score.

To select an alternative rating system pass one of the following ratings parameters:

- http://localhost:8080/?rating=Internet%20Movie%20Database
- http://localhost:8080/?rating=Rotten%20Tomatoes
- http://localhost:8080/?rating=Metacritic

## Todo

- Extract input and output file choice to config
- Create a front end for viewing table
- Create links to redux
- Change all json parsing to spray-json