var apiURL = 'data';

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