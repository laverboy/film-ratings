var apiURL = 'data';

new Vue({

  el: '#demo',

  data: {
    currentBranch: 'dev',
    critic: 'Internet Movie Database',
    items: null
  },

  created: function () {
    this.fetchData();
  },

  watch: {
    critic: function(){
        this.fetchData()
    }
  },

  methods: {
    fetchData: function () {
        var self = this;
        $.get( apiURL + '?rating=' + this.critic, function( data ) {
            self.items = data;
        });
    }

  }
});