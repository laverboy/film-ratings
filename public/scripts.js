var apiURL = 'data';

Vue.filter('formatName', function(value) {
  if (value == "Internet Movie Database") {
    return "IMDB"
  }
  return value
})

Vue.filter('formatClassName', function(value) {
    switch(value) {
        case "Internet Movie Database":
            return "imdb"
        case "Rotten Tomatoes":
            return "rt"
        case "Metacritic":
            return "meta"
        default:
            return value
    }
})

new Vue({

  el: '#demo',

  data: {
    currentBranch: 'dev',
    critic: 'Internet Movie Database',
    threshold: 80,
    items: null
  },

  created: function () {
    this.fetchData();
  },

  watch: {
    critic: function(){
        this.fetchData()
    },
    threshold: function(){
        this.fetchData()
    }
  },

  methods: {
    fetchData: function () {
        var self = this;
        $.get( apiURL + '?rating=' + this.critic + '&threshold=' + this.threshold, function( data ) {
            self.items = data;
        });
    }

  }
});